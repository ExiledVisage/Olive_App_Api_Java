package com.olivamore.api.inventory.storagemovement;

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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tank kurallarinin okumalari: anlik tank icerigi SQL ile (cagiran yazilanlari once veritabanina isler),
 * kategori, parti ve attribute tanimlari o modullerin arayuzlerinden.
 */
@Component
class TankQueries {

	record TankMaterial(Long id, Long productionProductTypeId, BigDecimal quantity) {
	}

	record DecimalValue(Long materialId, Long definitionId, BigDecimal valueDecimal) {
	}

	record ProductType(Long id, Long productionProductId, String name, String unit) {
	}

	record BatchProcess(Long id, Long productionBatchId, Long productionStageId) {
	}

	record RuleDefinition(Long id, Long productionProductId, Long attributeDefinitionId, String dataType,
		String label,
		String attributeKey) {
	}

	record BatchOutput(Long productionProductTypeId, String name) {
	}

	private final JdbcClient jdbc;
	private final ProductDirectory products;
	private final BatchDirectory batches;

	TankQueries(JdbcClient jdbc, ProductDirectory products, BatchDirectory batches) {
		this.jdbc = jdbc;
		this.products = products;
		this.batches = batches;
	}

	Map<Long, ProductType> productTypes(Collection<Long> ids) {
		return products.productTypes(ids).values().stream().collect(Collectors.toMap(ProductDirectory.ProductType::id,
			t -> new ProductType(t.id(), t.productionProductId(), t.name(), t.unit())));
	}

	Optional<ProductType> productType(long id) {
		return Optional.ofNullable(productTypes(List.of(id)).get(id));
	}

	Optional<BatchProcess> batchProcess(long id) {
		return batches.batchProcess(id)
			.map(p -> new BatchProcess(p.id(), p.productionBatchId(), p.productionStageId()));
	}

	boolean unitExists(long id) {
		return exists("storage_units", id);
	}

	boolean productTypeExists(long id) {
		return !products.existingTypes(List.of(id)).isEmpty();
	}

	boolean materialExists(long id) {
		return exists("materials", id);
	}

	boolean batchProcessExists(long id) {
		return batches.batchProcessExists(id);
	}

	Set<Long> existingDefinitions(Collection<Long> ids) {
		return products.existingAttributeMappings(ids);
	}

	private boolean exists(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where id = :id").param("id", id)
			.query(Long.class).single() > 0;
	}

	/** Partinin cikti urun tipleri ve adlari (sort_order). */
	List<BatchOutput> batchOutputs(long batchId) {
		return batches.batchOutputs(batchId).stream()
			.map(o -> new BatchOutput(o.productionProductTypeId(), o.name())).toList();
	}

	Optional<String> batchCode(long batchId) {
		return batches.batchCode(batchId);
	}

	/** Tanktaki aktif materyaller. */
	List<TankMaterial> activeMaterials(long unitId) {
		return jdbc.sql("""
			select id, production_product_type_id, quantity from materials
			where current_location_type = 'storage_unit' and current_location_id = :unit and status = 'active'
			""").param("unit", unitId).query(TankMaterial.class).list();
	}

	List<DecimalValue> decimalValues(Collection<Long> materialIds) {
		List<Long> present = ValidationErrors.present(materialIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select material_id, production_product_attribute_definition_id as definition_id, value_decimal
			from material_attribute_values where material_id in (:ids)
			""").param("ids", present).query(DecimalValue.class).list();
	}

	/** Kuraldaki tanimlar: urun-attribute eslemesi ve attribute'un veri tipi. */
	Map<Long, RuleDefinition> ruleDefinitions(Collection<Long> ids) {
		return products.attributeMappings(ids).values().stream().collect(Collectors.toMap(
			ProductDirectory.AttributeMapping::id,
			m -> new RuleDefinition(m.id(), m.productionProductId(), m.attributeDefinitionId(), m.dataType(), m.label(),
				m.attributeKey())));
	}

	/** Kategorinin attribute tanimlarinin id'leri. */
	Set<Long> definitionsOfProduct(Long productId) {
		return products.attributeMappingIdsOfProduct(productId);
	}

	/** Tanktaki toplam bakiye (tankin biriminde). */
	BigDecimal balanceTotal(long unitId, String unit) {
		return jdbc.sql("""
			select coalesce(sum(current_quantity), 0) from storage_unit_balances
			where storage_unit_id = :u and unit = :n
			""").param("u", unitId).param("n", unit).query(BigDecimal.class).single();
	}

	/** Aktif materyallerin ayni kategori ve birimdeki toplami. */
	BigDecimal materialTotal(long unitId, String unit, Long productId) {
		List<Long> typeIds = productId == null ? List.of() : products.typeIdsOfProducts(List.of(productId));
		if (typeIds.isEmpty()) {
			return BigDecimal.ZERO;
		}
		return jdbc.sql("""
			select coalesce(sum(quantity), 0) from materials where current_location_type = 'storage_unit'
				and current_location_id = :u and status = 'active' and unit = :n
				and production_product_type_id in (:types)
			""").param("u", unitId).param("n", unit).param("types", typeIds).query(BigDecimal.class).single();
	}

	/** Materyal bolunurken attribute degerleri cocuga kopyalanir; ayni sirayla ve yeni id'lerle. */
	void copyAttributeValues(long fromMaterialId, long toMaterialId, LocalDateTime now) {
		jdbc.sql("""
			insert into material_attribute_values (material_id, production_product_attribute_definition_id, value_boolean,
				value_integer, value_decimal, value_text, value_json, created_at, updated_at)
			select :to, production_product_attribute_definition_id, value_boolean, value_integer, value_decimal,
				value_text, value_json, :now, :now
			from material_attribute_values where material_id = :from order by id
			""").param("to", toMaterialId).param("from", fromMaterialId).param("now", now).update();
	}

}
