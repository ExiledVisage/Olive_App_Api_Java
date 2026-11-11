package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.BatchDirectory;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

	/** @param density etkin yogunluk (kg/L); yoksa null */
	record ProductType(Long id, Long productionProductId, String name, String unit, BigDecimal density) {
	}

	record Content(Long productionProductTypeId, BigDecimal quantity, String unit) {
	}

	record BatchProcess(Long id, Long productionBatchId, Long productionStageId) {
	}

	record RuleDefinition(Long id, Long productionProductId, Long attributeDefinitionId, String dataType,
		String label,
		String attributeKey) {
	}

	record BatchOutput(Long productionProductTypeId, String name) {
	}

	record PreSettlingEntry(Long storageUnitId, Long materialId) {
	}

	private record EntryProcess(Long materialId, Long batchProcessId) {
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
			t -> new ProductType(t.id(), t.productionProductId(), t.name(), t.unit(), t.effectiveDensity())));
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

	/** Tanktaki aktif materyallerin id'leri (id sirasiyla). */
	List<Long> activeMaterialIds(long unitId) {
		return jdbc.sql("""
			select id from materials
			where current_location_type = 'storage_unit' and current_location_id = :unit and status = 'active'
			order by id
			""").param("unit", unitId).query(Long.class).list();
	}

	/** Son tankta tipin aktif tank materyali. */
	Optional<Long> tankMix(long unitId, long typeId) {
		return jdbc.sql("""
			select id from materials
			where current_location_type = 'storage_unit' and current_location_id = :unit and status = 'active'
				and origin_type = 'TANK' and production_product_type_id = :type
			order by id limit 1
			""").param("unit", unitId).param("type", typeId).query(Long.class).optional();
	}

	/** Kategorinin zorunlu attribute tanimlari: esleme id'si ve etiketi (sort_order, id sirasiyla). */
	Map<Long, String> requiredDefinitions(Long productId) {
		return products.attributeMappings(products.attributeMappingIdsOfProduct(productId)).values().stream()
			.filter(m -> Boolean.TRUE.equals(m.isRequired()))
			.sorted(Comparator.comparing((ProductDirectory.AttributeMapping m) -> Objects.requireNonNullElse(m.sortOrder(),
				Integer.MAX_VALUE)).thenComparing(ProductDirectory.AttributeMapping::id))
			.collect(Collectors.toMap(ProductDirectory.AttributeMapping::id,
				m -> m.label() != null ? m.label() : Objects.requireNonNullElse(m.attributeKey(), ""),
				(a, b) -> a, LinkedHashMap::new));
	}

	/** Materyalde degeri dolu olan tanimlar. */
	Set<Long> definitionsWithValue(long materialId) {
		return Set.copyOf(jdbc.sql("""
			select production_product_attribute_definition_id from material_attribute_values
			where material_id = :material and (value_boolean is not null or value_integer is not null
				or value_decimal is not null or (value_text is not null and value_text <> '') or value_json is not null)
			""").param("material", materialId).query(Long.class).list());
	}

	/** Eslemelerin veri tipi (attribute tanimindan). */
	Map<Long, String> dataTypes(Collection<Long> mappingIds) {
		Map<Long, String> types = new HashMap<>();
		products.attributeMappings(mappingIds).values().forEach(m -> types.put(m.id(), m.dataType()));
		return types;
	}

	/** Tanktaki aktif materyallerin tipi, miktari ve birimi. */
	List<Content> contents(long unitId) {
		return jdbc.sql("""
			select production_product_type_id, quantity, unit from materials
			where current_location_type = 'storage_unit' and current_location_id = :unit and status = 'active'
			order by id
			""").param("unit", unitId).query(Content.class).list();
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

	/** Partinin tankta aktif materyali var mi. */
	boolean batchInTank(long unitId, long batchId) {
		return jdbc.sql("""
			select count(*) from materials
			where current_location_type = 'storage_unit' and current_location_id = :unit and status = 'active'
				and production_batch_id = :batch
			""").param("unit", unitId).param("batch", batchId).query(Long.class).single() > 0;
	}

	/** Prosesin on dinlendirme tanklarina giris hareketleri: tank ve tanka giren materyal (id sirasiyla). */
	List<PreSettlingEntry> preSettlingEntries(long batchProcessId) {
		return jdbc.sql("""
			select m.storage_unit_id, m.material_id from storage_movements m
			join storage_units u on u.id = m.storage_unit_id
			where m.batch_process_id = :process and m.movement_type = 'IN' and u.kind = 'pre_settling'
			order by m.id
			""").param("process", batchProcessId).query(PreSettlingEntry.class).list();
	}

	/** Materyalleri tanka sokan son giris hareketinin prosesi; prosessiz girenler haritada yok. */
	Map<Long, Long> entryProcesses(long unitId, Collection<Long> materialIds) {
		List<Long> present = ValidationErrors.present(materialIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select distinct on (material_id) material_id, batch_process_id from storage_movements
			where storage_unit_id = :unit and material_id in (:ids) and movement_type = 'IN'
				and batch_process_id is not null
			order by material_id, id desc
			""").param("unit", unitId).param("ids", present).query(EntryProcess.class).list().stream()
			.collect(Collectors.toMap(EntryProcess::materialId, EntryProcess::batchProcessId));
	}

	/** Verilen proseslerden dinlenmede olanlar. */
	Set<Long> restingProcesses(Collection<Long> batchProcessIds) {
		return batches.batchProcesses(batchProcessIds).values().stream()
			.filter(p -> "resting".equals(p.status())).map(BatchDirectory.BatchProcess::id).collect(Collectors.toSet());
	}

	/** Kapanan materyallerden verilen proseslerin actigi cocuklar (tortu), id sirasiyla. */
	List<Long> childrenCreatedBy(Collection<Long> parentIds, Collection<Long> processIds) {
		if (parentIds.isEmpty() || processIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select id from materials where parent_material_id in (:parents) and created_by_batch_process_id in (:processes)
			order by id
			""").param("parents", parentIds).param("processes", processIds).query(Long.class).list();
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
