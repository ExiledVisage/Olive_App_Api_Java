package com.olivamore.api.production.batch;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.supplier.SupplierDeliverySource;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Tedarikci ekranindaki teslimatlar: parti, girdi tipleri ve ilk kantar tartimi. */
@Component
class SupplierDeliveryReadModel implements SupplierDeliverySource {

	private static final String WEIGHING = "weighing";

	private record InputRow(Long batchId, Long productionProductTypeId) {
	}

	private record ProcessRow(Long id, Long batchId, Long productionStageId, String quantityJson,
		LocalDateTime completedAt) {
	}

	private record ValueRow(Long processId, Long processTypeFieldId, String valueText, BigDecimal valueDecimal,
		Long valueInteger) {
	}

	private final JdbcClient jdbc;
	private final ProductionBatchRepository batches;
	private final ProductDirectory products;
	private final ProductionLineResources lines;
	private final ProcessResources process;

	SupplierDeliveryReadModel(JdbcClient jdbc, ProductionBatchRepository batches, ProductDirectory products,
		ProductionLineResources lines, ProcessResources process) {
		this.jdbc = jdbc;
		this.batches = batches;
		this.products = products;
		this.lines = lines;
		this.process = process;
	}

	@Override
	public List<BatchDelivery> batches(long supplierId, LocalDate from, LocalDate to) {
		boolean prices = Purchases.visible();
		List<ProductionBatch> deliveries = batches.findAll((root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			where.add(cb.equal(root.get("supplierId"), supplierId));
			where.add(cb.isNull(root.get("parentBatchId")));
			Expression<LocalDate> day = cb.function("date", LocalDate.class, root.get("receivedAt"));
			if (from != null) {
				where.add(cb.greaterThanOrEqualTo(day, from));
			}
			if (to != null) {
				where.add(cb.lessThanOrEqualTo(day, to));
			}
			return cb.and(where.toArray(Predicate[]::new));
		}, Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id")));
		return deliveries.stream().map(b -> new BatchDelivery(b.getId(), b.getBatchCode(), b.getStatus().value(),
			b.getReceivedAt(), b.getSupplierFieldId(), b.getQuantityKg(), b.getTruckPlate(), b.getWeighingSlipNo(),
			prices ? purchase(b) : null)).toList();
	}

	@Override
	public boolean pricesVisible() {
		return Purchases.visible();
	}

	private static Purchase purchase(ProductionBatch batch) {
		ProductionBatchResponse.Purchase p = Purchases.of(batch, batch.getQuantityKg(), false);
		return p == null ? null : new Purchase(p.supplyChannel() == null ? null : p.supplyChannel().value(),
			p.pricePerKg(), p.vatRate(), p.pricePerKgWithVat(), p.transportCost(), p.totalExVat(), p.totalWithVat());
	}

	@Override
	public Map<Long, List<String>> inputProductNames(List<Long> batchIds) {
		List<InputRow> rows = jdbc.sql("""
			select production_batch_id as batch_id, production_product_type_id
			from production_batch_input_production_product_types
			where production_batch_id in (:ids)
			order by sort_order, id
			""").param("ids", batchIds).query(InputRow.class).list();
		Map<Long, ProductDirectory.ProductType> types = products.productTypes(
			rows.stream().map(InputRow::productionProductTypeId).toList());
		return rows.stream()
			.filter(row -> types.containsKey(row.productionProductTypeId())
				&& !"".equals(types.get(row.productionProductTypeId()).name()))
			.collect(Collectors.groupingBy(InputRow::batchId, LinkedHashMap::new, Collectors.mapping(
				row -> types.get(row.productionProductTypeId()).name(), Collectors.toList())));
	}

	@Override
	public Map<Long, WeighIn> firstWeighIns(List<Long> batchIds) {
		List<ProcessRow> rows = jdbc.sql("""
			select p.id, p.production_batch_id as batch_id, p.production_stage_id,
				(p.operation_data -> 'quantity')::text as quantity_json, p.completed_at
			from batch_processes p where p.production_batch_id in (:ids)
			""").param("ids", batchIds).query(ProcessRow.class).list();
		Map<Long, ProductionLineResources.Stage> stages = lines.stages(
			rows.stream().map(ProcessRow::productionStageId).distinct().toList());
		Set<Long> weighingTypes = process.processTypeIdsInCategory(WEIGHING);
		Comparator<ProcessRow> order = Comparator
			.<ProcessRow>comparingInt(row -> stages.get(row.productionStageId()).stageOrder())
			.thenComparing(ProcessRow::id);
		Map<Long, WeighIn> first = new LinkedHashMap<>();
		rows.stream()
			.filter(row -> stages.containsKey(row.productionStageId())
				&& weighingTypes.contains(stages.get(row.productionStageId()).processTypeId()))
			.sorted(order)
			.forEach(row -> first.putIfAbsent(row.batchId(),
				new WeighIn(row.id(), row.batchId(), row.quantityJson(), row.completedAt())));
		return first;
	}

	@Override
	public List<WeighInValue> fieldValues(List<Long> processIds) {
		if (processIds.isEmpty()) {
			return List.of();
		}
		List<ValueRow> rows = jdbc.sql("""
			select batch_process_id as process_id, process_type_field_id, value_text, value_decimal, value_integer
			from batch_process_field_values where batch_process_id in (:ids)
			order by id
			""").param("ids", processIds).query(ValueRow.class).list();
		Map<Long, ProcessResources.Field> fields = process.fields(
			rows.stream().map(ValueRow::processTypeFieldId).toList());
		return rows.stream().map(row -> {
			ProcessResources.Field field = fields.get(row.processTypeFieldId());
			return new WeighInValue(row.processId(), field == null ? null : field.fieldKey(),
				field == null ? null : field.label(), row.valueText(), row.valueDecimal(), row.valueInteger());
		}).toList();
	}

}
