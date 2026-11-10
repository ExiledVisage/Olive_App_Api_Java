package com.olivamore.api.inventory.material;

import com.olivamore.api.inventory.BatchDirectory;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Materyal cevaplarinin ve tank hesaplarinin okumalari: kendi tablolari SQL ile, urun ve parti bilgileri
 * o modullerin arayuzlerinden.
 */
@Component
public class MaterialQueries {

	public record MaterialRow(Long id, Long productionBatchId, Long productionProductTypeId, Long parentMaterialId,
		String originType, BigDecimal quantity, String unit, String status, String currentLocationType,
		Long currentLocationId, Long sourceProductionStageId, Long createdByBatchProcessId,
		Long closedByBatchProcessId, String note, LocalDateTime createdAt, LocalDateTime updatedAt) {
	}

	public record ProductType(Long id, Long productionProductId, String name, String code, String type, String unit,
		BigDecimal densityKgPerLitre) {
	}

	public record Product(Long id, String key, String name, String description, BigDecimal defaultDensityKgPerLitre) {
	}

	public record Batch(Long id, String batchCode, String status) {
	}

	public record BatchProcess(Long id, Long productionBatchId, Long productionStageId, String status) {
	}

	public record Unit(Long id, String name, String code) {
	}

	public record Definition(Long id, Long productionProductId, Long attributeDefinitionId, Boolean isRequired,
		Integer sortOrder, String attributeKey, String label, String dataType, String unit, String description) {
	}

	public record AttributeValueRow(Long id, Long materialId, Long definitionId, Boolean valueBoolean,
		Long valueInteger,
		BigDecimal valueDecimal, String valueText, String valueJson, LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	}

	private final JdbcClient jdbc;
	private final ProductDirectory products;
	private final BatchDirectory batches;

	MaterialQueries(JdbcClient jdbc, ProductDirectory products, BatchDirectory batches) {
		this.jdbc = jdbc;
		this.products = products;
		this.batches = batches;
	}

	public Optional<MaterialRow> material(long id) {
		return jdbc.sql("select * from materials where id = :id").param("id", id).query(MaterialRow.class).optional();
	}

	public Map<Long, MaterialRow> materials(Collection<Long> ids) {
		return byId("select * from materials where id in (:ids)", ids, MaterialRow.class, MaterialRow::id);
	}

	public List<MaterialRow> children(Collection<Long> parentIds) {
		List<Long> present = ValidationErrors.present(parentIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select * from materials where parent_material_id in (:ids) order by id asc")
			.param("ids", present).query(MaterialRow.class).list();
	}

	/** Materyalin cocuklari; sira verilmez (detay ekrani). */
	public List<MaterialRow> childrenOf(long parentId) {
		return jdbc.sql("select * from materials where parent_material_id in (:id)").param("id", List.of(parentId))
			.query(MaterialRow.class).list();
	}

	public Map<Long, ProductType> productTypes(Collection<Long> ids) {
		return products.productTypes(ids).values().stream().collect(Collectors.toMap(ProductDirectory.ProductType::id,
			t -> new ProductType(t.id(), t.productionProductId(), t.name(), t.code(), t.type(), t.unit(),
				t.densityKgPerLitre())));
	}

	public Map<Long, Product> products(Collection<Long> ids) {
		return this.products.products(ids).values().stream().collect(Collectors.toMap(ProductDirectory.Product::id,
			p -> new Product(p.id(), p.key(), p.name(), p.description(), p.defaultDensityKgPerLitre())));
	}

	public Map<Long, Batch> batches(Collection<Long> ids) {
		return this.batches.batches(ids).values().stream().collect(Collectors.toMap(BatchDirectory.Batch::id,
			b -> new Batch(b.id(), b.batchCode(), b.status())));
	}

	public Map<Long, BatchProcess> batchProcesses(Collection<Long> ids) {
		return this.batches.batchProcesses(ids).values().stream().collect(Collectors.toMap(
			BatchDirectory.BatchProcess::id,
			p -> new BatchProcess(p.id(), p.productionBatchId(), p.productionStageId(), p.status())));
	}

	public Map<Long, Unit> units(Collection<Long> ids) {
		return byId("select * from storage_units where id in (:ids)", ids, Unit.class, Unit::id);
	}

	/** Urun-attribute eslemesi ve attribute tanimi (etiket, veri tipi, birim, aciklama). */
	public Map<Long, Definition> definitions(Collection<Long> ids) {
		return products.attributeMappings(ids).values().stream().collect(Collectors.toMap(
			ProductDirectory.AttributeMapping::id,
			m -> new Definition(m.id(), m.productionProductId(), m.attributeDefinitionId(), m.isRequired(),
				m.sortOrder(), m.attributeKey(), m.label(), m.dataType(), m.unit(), m.description())));
	}

	/** Materyallerin attribute degerleri. */
	public List<AttributeValueRow> attributeValues(Collection<Long> materialIds) {
		List<Long> present = ValidationErrors.present(materialIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select id, material_id, production_product_attribute_definition_id as definition_id, value_boolean,
				value_integer, value_decimal, value_text, value_json::text as value_json, created_at, updated_at
			from material_attribute_values where material_id in (:ids)
			""").param("ids", present).query(AttributeValueRow.class).list();
	}

	private <T> Map<Long, T> byId(String sql, Collection<Long> ids, Class<T> type, Function<T, Long> key) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql(sql).param("ids", present).query(type).list().stream()
			.collect(Collectors.toMap(key, Function.identity()));
	}

}
