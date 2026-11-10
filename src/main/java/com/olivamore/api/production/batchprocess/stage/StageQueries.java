package com.olivamore.api.production.batchprocess.stage;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Uretim isleyicilerinin okumalari: asamalar, uzantilar, urun tipleri, materyaller (baska modullerin tablolari). */
@Component
public class StageQueries {

	public record Stage(Long id, Long productionLineId, Long processTypeId, String name, int stageOrder,
		boolean isRequired, boolean requiresApproval) {

		public String label() {
			return name != null ? name : "#" + id;
		}

	}

	public record ProductType(Long id, Long productionProductId, String name, String code, String type, String unit) {
	}

	public record OutputProduct(Long productionProductId, String productKey) {
	}

	public record ExtensionValue(String fieldKey, String valueText) {
	}

	/** Asamanin uzantisi: tip anahtari, girdi kategorileri, cikti kategorileri, degerler. */
	public record Extension(String key, boolean enabled, List<Long> inputProductIds, List<OutputProduct> outputProducts,
		List<ExtensionValue> values) {
	}

	public record Material(Long id, Long productionBatchId, Long productionProductTypeId, BigDecimal quantity, String unit) {
	}

	public record AttributeDefinition(Long id, Long attributeDefinitionId) {
	}

	public record Field(Long id, Long processTypeId, String fieldKey, String label, String dataType, boolean isRequired,
		String unit, Integer sortOrder, boolean isActive) {
	}

	private record ExtensionRow(Long id, Long productionStageId, Long stageExtensionTypeId, boolean isEnabled) {
	}

	private record ExtensionProductRow(Long productionStageExtensionId, Long productionProductId, String productKey) {
	}

	private record ExtensionValueRow(Long productionStageExtensionId, String fieldKey, String valueText) {
	}

	private final JdbcClient jdbc;

	StageQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Map<Long, Stage> stages(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select * from production_stages where id in (:ids)").param("ids", ids)
			.query(Stage.class).list().stream().collect(Collectors.toMap(Stage::id, Function.identity()));
	}

	public Stage stage(long id) {
		return stages(List.of(id)).get(id);
	}

	public int lastStageOrder(long lineId) {
		return jdbc.sql("select max(stage_order) from production_stages where production_line_id = :line")
			.param("line", lineId).query(Integer.class).single();
	}

	/** Asamanin uzantilari (sort_order sirasinda) ve iliskileri. */
	public List<Extension> extensions(long stageId) {
		List<ExtensionRow> rows = jdbc.sql("""
			select id, production_stage_id, stage_extension_type_id, is_enabled from production_stage_extensions
			where production_stage_id = :stage order by sort_order asc
			""").param("stage", stageId).query(ExtensionRow.class).list();
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> ids = rows.stream().map(ExtensionRow::id).toList();
		Map<Long, String> keys = jdbc.sql("select id, key from stage_extension_types where id in (:ids)")
			.param("ids", rows.stream().map(ExtensionRow::stageExtensionTypeId).toList())
			.query((rs, n) -> Map.entry(rs.getLong("id"), rs.getString("key"))).list().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		Map<Long, List<Long>> inputs = new LinkedHashMap<>();
		jdbc.sql("""
			select production_stage_extension_id, production_product_id, null as product_key
			from production_stage_extension_input_production_products
			where production_stage_extension_id in (:ids) order by sort_order asc
			""").param("ids", ids).query(ExtensionProductRow.class).list().forEach(row -> inputs
			.computeIfAbsent(row.productionStageExtensionId(), k -> new ArrayList<>()).add(row.productionProductId()));
		Map<Long, List<OutputProduct>> outputs = new LinkedHashMap<>();
		jdbc.sql("""
			select o.production_stage_extension_id, o.production_product_id, p.key as product_key
			from production_stage_extension_output_production_products o
			left join production_products p on p.id = o.production_product_id
			where o.production_stage_extension_id in (:ids) order by o.sort_order asc
			""").param("ids", ids).query(ExtensionProductRow.class).list().forEach(row -> outputs
			.computeIfAbsent(row.productionStageExtensionId(), k -> new ArrayList<>())
			.add(new OutputProduct(row.productionProductId(), row.productKey())));
		Map<Long, List<ExtensionValue>> values = new LinkedHashMap<>();
		jdbc.sql("""
			select v.production_stage_extension_id, f.field_key, v.value_text
			from production_stage_extension_values v
			left join stage_extension_type_fields f on f.id = v.stage_extension_type_field_id
			where v.production_stage_extension_id in (:ids)
			""").param("ids", ids).query(ExtensionValueRow.class).list().forEach(row -> values
			.computeIfAbsent(row.productionStageExtensionId(), k -> new ArrayList<>())
			.add(new ExtensionValue(row.fieldKey(), row.valueText())));
		return rows.stream().map(row -> new Extension(keys.get(row.stageExtensionTypeId()), row.isEnabled(),
			inputs.getOrDefault(row.id(), List.of()), outputs.getOrDefault(row.id(), List.of()),
			values.getOrDefault(row.id(), List.of()))).toList();
	}

	public boolean exists(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where id = :id").param("id", id).query(Long.class).single() > 0;
	}

	// ---- urun tipleri ------------------------------------------------------

	public Optional<ProductType> productType(long id) {
		return jdbc.sql("select * from production_product_types where id = :id").param("id", id)
			.query(ProductType.class).optional();
	}

	/** Partinin giris urun tipleri (sort_order). */
	public List<ProductType> inputTypes(long batchId) {
		return batchTypes("production_batch_input_production_product_types", batchId);
	}

	/** Partinin cikis urun tipleri (sort_order). */
	public List<ProductType> outputTypes(long batchId) {
		return batchTypes("production_batch_output_production_product_types", batchId);
	}

	private List<ProductType> batchTypes(String table, long batchId) {
		return jdbc.sql("select t.* from " + table + " l join production_product_types t"
				+ " on t.id = l.production_product_type_id where l.production_batch_id = :batch order by l.sort_order asc")
			.param("batch", batchId).query(ProductType.class).list();
	}

	// ---- materyaller -------------------------------------------------------

	private static final String MATERIAL_COLUMNS =
		"m.id, m.production_batch_id, m.production_product_type_id, m.quantity, m.unit";

	public Optional<Material> material(long id) {
		return jdbc.sql("select " + MATERIAL_COLUMNS + " from materials m where m.id = :id").param("id", id)
			.query(Material.class).optional();
	}

	/** Partinin aktif materyalleri, tipi verilen kategorilerden. */
	public List<Material> activeMaterials(long batchId, Collection<Long> productIds) {
		return jdbc.sql("select " + MATERIAL_COLUMNS + """
			 from materials m
			where m.production_batch_id = :batch and m.status = 'active' and exists (
				select 1 from production_product_types t
				where t.id = m.production_product_type_id and t.production_product_id in (:products))
			""").param("batch", batchId).param("products", productIds).query(Material.class).list();
	}

	public List<Material> activeMaterials(long batchId) {
		return jdbc.sql("select " + MATERIAL_COLUMNS + " from materials m where m.production_batch_id = :batch"
			+ " and m.status = 'active'").param("batch", batchId).query(Material.class).list();
	}

	public Optional<Material> activeMaterialOfType(long batchId, long typeId) {
		return jdbc.sql("select " + MATERIAL_COLUMNS + " from materials m where m.production_batch_id = :batch"
				+ " and m.production_product_type_id = :type and m.status = 'active' limit 1")
			.param("batch", batchId).param("type", typeId).query(Material.class).optional();
	}

	public long materialsCreatedBy(long processId) {
		return jdbc.sql("select count(*) from materials where created_by_batch_process_id = :id").param("id", processId)
			.query(Long.class).single();
	}

	public long movementsOf(long processId) {
		return jdbc.sql("select count(*) from storage_movements where batch_process_id = :id").param("id", processId)
			.query(Long.class).single();
	}

	// ---- attribute tanimlari ----------------------------------------------

	public Optional<AttributeDefinition> definition(long productId, long id) {
		return jdbc.sql("""
			select id, attribute_definition_id from production_product_attribute_definitions
			where production_product_id = :product and id = :id limit 1
			""").param("product", productId).param("id", id).query(AttributeDefinition.class).optional();
	}

	public Optional<AttributeDefinition> definitionByKey(long productId, String attributeKey) {
		return jdbc.sql("""
			select d.id, d.attribute_definition_id from production_product_attribute_definitions d
			where d.production_product_id = :product and exists (
				select 1 from attribute_definitions a where a.id = d.attribute_definition_id and a.attribute_key = :key)
			limit 1
			""").param("product", productId).param("key", attributeKey).query(AttributeDefinition.class).optional();
	}

	public Optional<String> attributeDataType(Long attributeDefinitionId) {
		if (attributeDefinitionId == null) {
			return Optional.empty();
		}
		return jdbc.sql("select data_type from attribute_definitions where id = :id").param("id", attributeDefinitionId)
			.query(String.class).optional();
	}

	// ---- proses alanlari --------------------------------------------------

	public Map<Long, Field> fields(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select * from process_type_fields where id in (:ids)").param("ids", ids)
			.query(Field.class).list().stream().collect(Collectors.toMap(Field::id, Function.identity()));
	}

	/** Proses tipinin alanlari (sort_order). */
	public List<Field> fieldsOfType(long processTypeId) {
		return jdbc.sql("select * from process_type_fields where process_type_id = :type order by sort_order asc")
			.param("type", processTypeId).query(Field.class).list();
	}

	public boolean userExists(long id) {
		return exists("users", id);
	}

}
