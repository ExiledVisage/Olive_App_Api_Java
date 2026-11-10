package com.olivamore.api.production.batch;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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

	private record FieldOwner(Long supplierId) {
	}

	private final JdbcClient jdbc;

	BatchQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	boolean exists(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where id = :id").param("id", id).query(Long.class).single() > 0;
	}

	Map<Long, ProductType> productTypes(Collection<Long> ids) {
		return byId("production_product_types", ids, ProductType.class, ProductType::id);
	}

	Optional<ProductType> productType(long id) {
		return Optional.ofNullable(productTypes(List.of(id)).get(id));
	}

	List<Long> existingProductTypes(Collection<Long> ids) {
		return List.copyOf(productTypes(ids).keySet());
	}

	Optional<ProductType> productTypeOf(long productId, String type) {
		return jdbc.sql("select * from production_product_types where production_product_id = :product and type = :type limit 1")
			.param("product", productId).param("type", type).query(ProductType.class).optional();
	}

	Optional<ProductType> untypedProductTypeOf(long productId) {
		return jdbc.sql("select * from production_product_types where production_product_id = :product and type is null limit 1")
			.param("product", productId).query(ProductType.class).optional();
	}

	Optional<String> productName(long id) {
		return jdbc.sql("select name from production_products where id = :id").param("id", id).query(String.class).optional();
	}

	Map<Long, Product> products(Collection<Long> ids) {
		return byId("production_products", ids, Product.class, Product::id);
	}

	Map<Long, LineRow> lines(Collection<Long> ids) {
		return byId("production_lines", ids, LineRow.class, LineRow::id);
	}

	List<LineProductRow> lineInputs(Collection<Long> lineIds) {
		return lineProducts("production_line_input_production_products", lineIds);
	}

	List<LineProductRow> lineOutputs(Collection<Long> lineIds) {
		return lineProducts("production_line_output_production_products", lineIds);
	}

	private List<LineProductRow> lineProducts(String table, Collection<Long> lineIds) {
		if (lineIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select * from " + table + " where production_line_id in (:ids) order by sort_order asc")
			.param("ids", lineIds).query(LineProductRow.class).list();
	}

	/** Hattin asamalari sirayla. */
	List<Long> stageIds(long lineId) {
		return jdbc.sql("select id from production_stages where production_line_id = :line order by stage_order asc")
			.param("line", lineId).query(Long.class).list();
	}

	Map<Long, Supplier> suppliers(Collection<Long> ids) {
		return byId("suppliers", ids, Supplier.class, Supplier::id);
	}

	Map<Long, SupplierField> supplierFields(Collection<Long> ids) {
		return byId("supplier_fields", ids, SupplierField.class, SupplierField::id);
	}

	Optional<Long> fieldOwner(long fieldId) {
		return jdbc.sql("select supplier_id from supplier_fields where id = :id").param("id", fieldId)
			.query(FieldOwner.class).optional().map(FieldOwner::supplierId);
	}

	List<MaterialLink> materialsOfBatches(Collection<Long> batchIds) {
		if (batchIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select id, production_batch_id from materials where production_batch_id in (:ids)")
			.param("ids", batchIds).query(MaterialLink.class).list();
	}

	long materialCount(long batchId) {
		return jdbc.sql("select count(*) from materials where production_batch_id = :id").param("id", batchId)
			.query(Long.class).single();
	}

	long movementCount(long batchId) {
		return jdbc.sql("""
			select count(*) from storage_movements
			where batch_process_id in (select id from batch_processes where production_batch_id = :id)
			""").param("id", batchId).query(Long.class).single();
	}

	/** Gun -> parti sayisi (varis gunu, dahil). */
	Map<String, Long> calendar(LocalDate from, LocalDate to) {
		Map<String, Long> days = new LinkedHashMap<>();
		jdbc.sql("""
			select date(received_at) as day, count(*) as total from production_batches
			where received_at is not null and received_at::date >= :from and received_at::date <= :to
			group by day order by day asc
			""").param("from", from).param("to", to).query(DayCount.class).list()
			.forEach(row -> days.put(row.day().toString(), row.total()));
		return days;
	}

	private <T> Map<Long, T> byId(String table, Collection<Long> ids, Class<T> type, Function<T, Long> key) {
		if (ids.isEmpty()) {
			return Collections.emptyMap();
		}
		return jdbc.sql("select * from " + table + " where id in (:ids)").param("ids", ids).query(type).list().stream()
			.collect(Collectors.toMap(key, Function.identity()));
	}

}
