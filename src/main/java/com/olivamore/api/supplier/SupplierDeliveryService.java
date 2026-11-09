package com.olivamore.api.supplier;

import com.olivamore.api.supplier.SupplierDeliveriesResponse.Delivery;
import com.olivamore.api.supplier.SupplierDeliveriesResponse.Summary;
import com.olivamore.api.supplier.SupplierDeliveriesResponse.WeighInField;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Uretim tablolarini yalnizca okur: production modulune bagimlilik yok. */
@Service
class SupplierDeliveryService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private record BatchRow(Long id, String batchCode, String status, LocalDateTime receivedAt, String fieldCode,
		BigDecimal quantityKg) {
	}

	private record ProductNameRow(Long batchId, String name) {
	}

	private record WeighInRow(Long id, Long batchId, String quantityJson, LocalDateTime completedAt) {
	}

	private record FieldValueRow(Long processId, String fieldKey, String label, String valueText,
		BigDecimal valueDecimal, Long valueInteger) {
	}

	private final JdbcClient jdbc;
	private final SupplierService suppliers;

	SupplierDeliveryService(JdbcClient jdbc, SupplierService suppliers) {
		this.jdbc = jdbc;
		this.suppliers = suppliers;
	}

	@Transactional(readOnly = true)
	SupplierDeliveriesResponse forSupplier(long supplierId) {
		suppliers.ensureExists(supplierId);
		List<BatchRow> batches = batches(supplierId);
		if (batches.isEmpty()) {
			return new SupplierDeliveriesResponse(List.of(), new Summary(0, BigDecimal.ZERO, null));
		}
		List<Long> ids = batches.stream().map(BatchRow::id).toList();
		Map<Long, List<String>> names = productNames(ids);
		Map<Long, WeighInRow> weighIns = weighIns(ids);
		Map<Long, List<WeighInField>> fields = fieldValues(weighIns.values().stream().map(WeighInRow::id).toList());

		List<Delivery> deliveries = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		for (BatchRow batch : batches) {
			WeighInRow weighIn = weighIns.get(batch.id());
			JsonNode quantity = weighIn == null || weighIn.quantityJson() == null
				? null : JSON.readTree(weighIn.quantityJson());
			total = total.add(number(quantity));
			deliveries.add(new Delivery(batch.id(), batch.batchCode(), batch.status(), batch.receivedAt(),
				batch.fieldCode(), names.getOrDefault(batch.id(), List.of()), batch.quantityKg(), quantity,
				weighIn == null ? null : weighIn.completedAt(),
				weighIn == null ? List.of() : fields.getOrDefault(weighIn.id(), List.of())));
		}
		BigDecimal rounded = new BigDecimal(
			total.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
		return new SupplierDeliveriesResponse(deliveries,
			new Summary(deliveries.size(), rounded, deliveries.getFirst().receivedAt()));
	}

	private List<BatchRow> batches(long supplierId) {
		return jdbc.sql("""
			select b.id, b.batch_code, b.status, b.received_at, f.code as field_code, b.quantity_kg
			from production_batches b
			left join supplier_fields f on f.id = b.supplier_field_id
			where b.supplier_id = ?
			order by b.received_at desc, b.id desc
			""").param(supplierId).query(BatchRow.class).list();
	}

	private Map<Long, List<String>> productNames(List<Long> batchIds) {
		return jdbc.sql("""
			select i.production_batch_id as batch_id, t.name
			from production_batch_input_production_product_types i
			join production_product_types t on t.id = i.production_product_type_id
			where i.production_batch_id in (:ids) and t.name <> ''
			order by i.sort_order, i.id
			""").param("ids", batchIds).query(ProductNameRow.class).list().stream()
			.collect(Collectors.groupingBy(ProductNameRow::batchId,
				Collectors.mapping(ProductNameRow::name, Collectors.toList())));
	}

	/** Partinin kategorisi "weighing" olan prosesleri arasindan asama sirasi en kucuk olan. */
	private Map<Long, WeighInRow> weighIns(List<Long> batchIds) {
		return jdbc.sql("""
			select distinct on (p.production_batch_id) p.id, p.production_batch_id as batch_id,
				(p.operation_data -> 'quantity')::text as quantity_json, p.completed_at
			from batch_processes p
			join production_stages s on s.id = p.production_stage_id
			join process_types t on t.id = s.process_type_id
			where p.production_batch_id in (:ids) and t.category = 'weighing'
			order by p.production_batch_id, s.stage_order, p.id
			""").param("ids", batchIds).query(WeighInRow.class).list().stream()
			.collect(Collectors.toMap(WeighInRow::batchId, row -> row));
	}

	private Map<Long, List<WeighInField>> fieldValues(List<Long> processIds) {
		if (processIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select v.batch_process_id as process_id, f.field_key, f.label, v.value_text, v.value_decimal,
				v.value_integer
			from batch_process_field_values v
			left join process_type_fields f on f.id = v.process_type_field_id
			where v.batch_process_id in (:ids)
			order by v.id
			""").param("ids", processIds).query(FieldValueRow.class).list().stream()
			.filter(row -> value(row) != null)
			.collect(Collectors.groupingBy(FieldValueRow::processId, Collectors.mapping(
				row -> new WeighInField(row.fieldKey(), row.label(), value(row)), Collectors.toList())));
	}

	private static Object value(FieldValueRow row) {
		if (row.valueText() != null) {
			return row.valueText();
		}
		return row.valueDecimal() != null ? row.valueDecimal().toPlainString() : row.valueInteger();
	}

	private static BigDecimal number(JsonNode node) {
		if (node == null || node.isNull()) {
			return BigDecimal.ZERO;
		}
		if (node.isNumber()) {
			return node.decimalValue();
		}
		try {
			return new BigDecimal(node.asString().strip());
		}
		catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

}
