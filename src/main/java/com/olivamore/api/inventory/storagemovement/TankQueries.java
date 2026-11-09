package com.olivamore.api.inventory.storagemovement;

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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tank kurallarinin okumalari (kategori, parti, attribute tanimlari ve anlik tank icerigi): baska modullerin
 * tablolari ve uretimin ayni islemde JDBC ile yazdigi satirlar; cagiran yazilanlari once veritabanina isler.
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

	TankQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Map<Long, ProductType> productTypes(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select * from production_product_types where id in (:ids)").param("ids", present)
			.query(ProductType.class).list().stream().collect(Collectors.toMap(ProductType::id, Function.identity()));
	}

	Optional<ProductType> productType(long id) {
		return Optional.ofNullable(productTypes(List.of(id)).get(id));
	}

	Optional<BatchProcess> batchProcess(long id) {
		return jdbc.sql("select id, production_batch_id, production_stage_id from batch_processes where id = :id")
			.param("id", id).query(BatchProcess.class).optional();
	}

	boolean unitExists(long id) {
		return exists("storage_units", id);
	}

	boolean productTypeExists(long id) {
		return exists("production_product_types", id);
	}

	boolean materialExists(long id) {
		return exists("materials", id);
	}

	boolean batchProcessExists(long id) {
		return exists("batch_processes", id);
	}

	Set<Long> existingDefinitions(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("select id from production_product_attribute_definitions where id in (:ids)")
			.param("ids", present).query(Long.class).list());
	}

	private boolean exists(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where id = :id").param("id", id)
			.query(Long.class).single() > 0;
	}

	/** Partinin cikti urun tipleri ve adlari (sort_order). */
	List<BatchOutput> batchOutputs(long batchId) {
		return jdbc.sql("""
			select o.production_product_type_id, t.name
			from production_batch_output_production_product_types o
			left join production_product_types t on t.id = o.production_product_type_id
			where o.production_batch_id = :batch
			order by o.sort_order asc
			""").param("batch", batchId).query(BatchOutput.class).list();
	}

	Optional<String> batchCode(long batchId) {
		return jdbc.sql("select batch_code from production_batches where id = :id").param("id", batchId)
			.query(String.class).optional();
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
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select m.id, m.production_product_id, m.attribute_definition_id, a.data_type, a.label, a.attribute_key
			from production_product_attribute_definitions m
			left join attribute_definitions a on a.id = m.attribute_definition_id
			where m.id in (:ids)
			""").param("ids", present).query(RuleDefinition.class).list().stream()
			.collect(Collectors.toMap(RuleDefinition::id, Function.identity()));
	}

	/** Kategorinin attribute tanimlarinin id'leri. */
	Set<Long> definitionsOfProduct(Long productId) {
		if (productId == null) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("""
			select id from production_product_attribute_definitions where production_product_id = :p
			""").param("p", productId).query(Long.class).list());
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
		return jdbc.sql("""
			select coalesce(sum(quantity), 0) from materials where current_location_type = 'storage_unit'
				and current_location_id = :u and status = 'active' and unit = :n
				and exists (select * from production_product_types t
					where materials.production_product_type_id = t.id and t.production_product_id = :p)
			""").param("u", unitId).param("n", unit).param("p", productId).query(BigDecimal.class).single();
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
