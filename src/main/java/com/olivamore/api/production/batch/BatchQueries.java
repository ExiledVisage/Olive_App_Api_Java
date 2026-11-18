package com.olivamore.api.production.batch;

import com.olivamore.api.inventory.MaterialDirectory;
import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.supplier.SupplierDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Parti kurallari ve cevabi icin baska modullerin tablolarindan okumalar. */
@Component
class BatchQueries {

	record ProductType(Long id, Long productionProductId, String name, String code, String type, String unit) {
	}

	record Product(Long id, String key, String name, String description) {
	}

	record Supplier(Long id, String name, String code) {
	}

	record SupplierField(Long id, String code, String location) {
	}

	record LineRow(Long id, String name, String description) {
	}

	record LineProductRow(Long id, Long productionLineId, Long productionProductId, Long productionProductTypeId,
		int sortOrder) {
	}

	record MaterialLink(Long id, Long productionBatchId) {
	}

	record DayCount(LocalDate day, long total) {
	}

	private final JdbcClient jdbc;
	private final ProductDirectory productDirectory;
	private final ProductionLineResources lineResources;
	private final SupplierDirectory supplierDirectory;
	private final MaterialDirectory materialDirectory;
	private final ProcessResources processResources;

	BatchQueries(JdbcClient jdbc, ProductDirectory productDirectory, ProductionLineResources lineResources,
		SupplierDirectory supplierDirectory, MaterialDirectory materialDirectory, ProcessResources processResources) {
		this.jdbc = jdbc;
		this.productDirectory = productDirectory;
		this.lineResources = lineResources;
		this.supplierDirectory = supplierDirectory;
		this.materialDirectory = materialDirectory;
		this.processResources = processResources;
	}

	boolean lineExists(long id) {
		return lineResources.lineExists(id);
	}

	boolean supplierExists(long id) {
		return supplierDirectory.supplierExists(id);
	}

	boolean fieldExists(long id) {
		return supplierDirectory.fieldExists(id);
	}

	Map<Long, ProductType> productTypes(Collection<Long> ids) {
		return productDirectory.productTypes(ids).values().stream().collect(Collectors.toMap(
			ProductDirectory.ProductType::id, BatchQueries::type));
	}

	private static ProductType type(ProductDirectory.ProductType t) {
		return new ProductType(t.id(), t.productionProductId(), t.name(), t.code(), t.type(), t.unit());
	}

	Optional<ProductType> productType(long id) {
		return Optional.ofNullable(productTypes(List.of(id)).get(id));
	}

	List<Long> existingProductTypes(Collection<Long> ids) {
		return List.copyOf(productTypes(ids).keySet());
	}

	Optional<ProductType> productTypeOf(long productId, String type) {
		return productDirectory.productTypeOf(productId, type).map(BatchQueries::type);
	}

	Optional<ProductType> untypedProductTypeOf(long productId) {
		return productDirectory.untypedProductTypeOf(productId).map(BatchQueries::type);
	}

	Optional<String> productName(long id) {
		return productDirectory.productName(id);
	}

	Map<Long, Product> products(Collection<Long> ids) {
		return productDirectory.products(ids).values().stream().collect(Collectors.toMap(
			ProductDirectory.Product::id, p -> new Product(p.id(), p.key(), p.name(), p.description())));
	}

	Map<Long, LineRow> lines(Collection<Long> ids) {
		return lineResources.lines(ids).values().stream().collect(Collectors.toMap(
			ProductionLineResources.Line::id, l -> new LineRow(l.id(), l.name(), l.description())));
	}

	List<LineProductRow> lineInputs(Collection<Long> lineIds) {
		return lineResources.lineInputs(lineIds).stream().map(BatchQueries::lineProduct).toList();
	}

	List<LineProductRow> lineOutputs(Collection<Long> lineIds) {
		return lineResources.lineOutputs(lineIds).stream().map(BatchQueries::lineProduct).toList();
	}

	private static LineProductRow lineProduct(ProductionLineResources.LineProduct p) {
		return new LineProductRow(p.id(), p.productionLineId(), p.productionProductId(), p.productionProductTypeId(),
			p.sortOrder());
	}

	/** Hattin asamalari sirayla. */
	List<Long> stageIds(long lineId) {
		return lineResources.stageIds(lineId);
	}

	List<ProductionLineResources.Stage> lineStages(long lineId) {
		return lineResources.lineStages(lineId);
	}

	/** Tartim kategorisindeki proses tipleri. */
	Set<Long> weighingTypes() {
		return processResources.processTypeIdsInCategory("weighing");
	}

	/** Partinin kendisinde duran (tanka alinmamis) aktif materyalleri. */
	List<MaterialDirectory.Material> activeMaterialsInBatch(long batchId) {
		return materialDirectory.activeOfBatch(batchId).stream()
			.filter(m -> "production_batch".equals(m.currentLocationType())
				&& Long.valueOf(batchId).equals(m.currentLocationId()))
			.toList();
	}

	Map<Long, Supplier> suppliers(Collection<Long> ids) {
		return supplierDirectory.suppliers(ids).values().stream().collect(Collectors.toMap(
			SupplierDirectory.Supplier::id, s -> new Supplier(s.id(), s.name(), s.code())));
	}

	Map<Long, SupplierField> supplierFields(Collection<Long> ids) {
		return supplierDirectory.fields(ids).values().stream().collect(Collectors.toMap(
			SupplierDirectory.Field::id, f -> new SupplierField(f.id(), f.code(), f.location())));
	}

	Optional<Long> fieldOwner(long fieldId) {
		return supplierDirectory.fieldOwner(fieldId);
	}

	List<MaterialLink> materialsOfBatches(Collection<Long> batchIds) {
		return materialDirectory.ofBatches(batchIds).stream()
			.map(m -> new MaterialLink(m.id(), m.productionBatchId())).toList();
	}

	long materialCount(long batchId) {
		return materialDirectory.countOfBatch(batchId);
	}

	long movementCount(long batchId) {
		List<Long> processIds = jdbc.sql("select id from batch_processes where production_batch_id = :id")
			.param("id", batchId).query(Long.class).list();
		return materialDirectory.countMovementsOf(processIds);
	}

	/** Gun -> teslimat sayisi (varis gunu, dahil; gruplar sayilmaz). */
	Map<String, Long> calendar(LocalDate from, LocalDate to) {
		Map<String, Long> days = new LinkedHashMap<>();
		jdbc.sql("""
			select date(received_at) as day, count(*) as total from production_batches
			where parent_batch_id is null and received_at is not null and received_at::date >= :from
				and received_at::date <= :to
			group by day order by day asc
			""").param("from", from).param("to", to).query(DayCount.class).list()
			.forEach(row -> days.put(row.day().toString(), row.total()));
		return days;
	}

}
