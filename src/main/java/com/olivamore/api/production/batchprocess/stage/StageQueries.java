package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.inventory.MaterialDirectory;
import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.productionline.StageExtensionResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Uretim isleyicilerinin okumalari: asamalar, uzantilar, urun tipleri, materyaller diger modullerin arayuzlerinden. */
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

	private final JdbcClient jdbc;
	private final ProductDirectory products;
	private final ProductionLineResources lines;
	private final MaterialDirectory materials;
	private final ProcessResources process;
	private final UserDirectory users;

	StageQueries(JdbcClient jdbc, ProductDirectory products, ProductionLineResources lines, MaterialDirectory materials,
		ProcessResources process, UserDirectory users) {
		this.jdbc = jdbc;
		this.products = products;
		this.lines = lines;
		this.materials = materials;
		this.process = process;
		this.users = users;
	}

	public Map<Long, Stage> stages(Collection<Long> ids) {
		return lines.stages(ids).values().stream().collect(Collectors.toMap(ProductionLineResources.Stage::id,
			s -> new Stage(s.id(), s.productionLineId(), s.processTypeId(), s.name(), s.stageOrder(), s.isRequired(),
				s.requiresApproval())));
	}

	public Stage stage(long id) {
		return stages(List.of(id)).get(id);
	}

	public int lastStageOrder(long lineId) {
		return lines.lastStageOrder(lineId);
	}

	/** Asamanin uzantilari (sort_order sirasinda) ve iliskileri. */
	public List<Extension> extensions(long stageId) {
		return lines.stageExtensions(List.of(stageId), true).getOrDefault(stageId, List.of()).stream()
			.map(StageQueries::extension).toList();
	}

	private static Extension extension(StageExtensionResponse e) {
		return new Extension(e.stageExtensionType() == null ? null : e.stageExtensionType().key(), e.isEnabled(),
			e.inputProductionProducts().stream().map(StageExtensionResponse.LinkedProduct::productionProductId).toList(),
			e.outputProductionProducts().stream().map(p -> new OutputProduct(p.productionProductId(),
				p.productionProduct() == null ? null : p.productionProduct().key())).toList(),
			e.values().stream()
				.map(v -> new ExtensionValue(v.field() == null ? null : v.field().fieldKey(), v.valueText()))
				.toList());
	}

	/** Dogrulama kurallarindaki id alanlari. */
	public boolean exists(String table, long id) {
		return switch (table) {
			case "production_products" -> !products.existingProducts(List.of(id)).isEmpty();
			case "production_product_types" -> !products.existingTypes(List.of(id)).isEmpty();
			case "production_product_attribute_definitions" ->
				!products.existingAttributeMappings(List.of(id)).isEmpty();
			case "storage_units" -> materials.tankExists(id);
			case "materials" -> materials.material(id).isPresent();
			default -> throw new IllegalArgumentException("Bilinmeyen tablo: " + table);
		};
	}

	// ---- urun tipleri ------------------------------------------------------

	public Optional<ProductType> productType(long id) {
		return products.productType(id).map(StageQueries::type);
	}

	private static ProductType type(ProductDirectory.ProductType t) {
		return new ProductType(t.id(), t.productionProductId(), t.name(), t.code(), t.type(), t.unit());
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
		List<Long> ids = jdbc.sql("select production_product_type_id from " + table
				+ " where production_batch_id = :batch order by sort_order asc, id asc")
			.param("batch", batchId).query(Long.class).list();
		Map<Long, ProductDirectory.ProductType> types = products.productTypes(ids);
		return ids.stream().filter(types::containsKey).map(id -> type(types.get(id))).toList();
	}

	// ---- materyaller -------------------------------------------------------

	private static Material material(MaterialDirectory.Material m) {
		return new Material(m.id(), m.productionBatchId(), m.productionProductTypeId(), m.quantity(), m.unit());
	}

	public Optional<Material> material(long id) {
		return materials.material(id).map(StageQueries::material);
	}

	/** Partinin aktif materyalleri, tipi verilen kategorilerden. */
	public List<Material> activeMaterials(long batchId, Collection<Long> productIds) {
		return materials.activeOfBatch(batchId, products.typeIdsOfProducts(productIds)).stream()
			.map(StageQueries::material).toList();
	}

	public List<Material> activeMaterials(long batchId) {
		return materials.activeOfBatch(batchId).stream().map(StageQueries::material).toList();
	}

	public Optional<Material> activeMaterialOfType(long batchId, long typeId) {
		return materials.activeOfBatchAndType(batchId, typeId).map(StageQueries::material);
	}

	public long materialsCreatedBy(long processId) {
		return materials.countCreatedBy(processId);
	}

	public long movementsOf(long processId) {
		return materials.countMovementsOf(List.of(processId));
	}

	// ---- attribute tanimlari ----------------------------------------------

	public Optional<AttributeDefinition> definition(long productId, long id) {
		return products.attributeMappingOf(productId, id)
			.map(m -> new AttributeDefinition(m.id(), m.attributeDefinitionId()));
	}

	public Optional<AttributeDefinition> definitionByKey(long productId, String attributeKey) {
		return products.attributeMappingByKey(productId, attributeKey)
			.map(m -> new AttributeDefinition(m.id(), m.attributeDefinitionId()));
	}

	public Optional<String> attributeDataType(Long attributeDefinitionId) {
		return products.attributeDataType(attributeDefinitionId);
	}

	// ---- proses alanlari --------------------------------------------------

	private static Field field(ProcessResources.Field f) {
		return new Field(f.id(), f.processTypeId(), f.fieldKey(), f.label(), f.dataType(), f.isRequired(), f.unit(),
			f.sortOrder(), f.isActive());
	}

	public Map<Long, Field> fields(Collection<Long> ids) {
		return process.fields(ids).values().stream()
			.collect(Collectors.toMap(ProcessResources.Field::id, StageQueries::field));
	}

	/** Proses tipinin alanlari (sort_order). */
	public List<Field> fieldsOfType(long processTypeId) {
		return process.fieldsOfType(processTypeId).stream().map(StageQueries::field).toList();
	}

	public boolean userExists(long id) {
		return users.exists(id);
	}

}
