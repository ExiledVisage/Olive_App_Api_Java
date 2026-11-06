package com.olivamore.api.catalog;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Katalogun yazma endpoint'leri. Her metotta Laravel'deki FormRequest kurallari (ayni sira) ve servis
 * mantigi. Cevap kodlari Laravel'deki gibi: {@code Model::create()} sonucu 201, {@code ->fresh()} ile
 * donen yeni ornek 200.
 */
@RestController
class CatalogWriteController {

	static final String PROCESS_CATEGORIES = "in:sourcing,weighing,sorting,transfer,measurement,quality,storage,machine,yield_analysis";
	static final String FIELD_TYPES = "in:text,decimal,integer,boolean,datetime,json";
	static final String ATTRIBUTE_TYPES = "in:boolean,integer,decimal,text,json";

	private final Db db;
	private final CatalogReads reads;
	private final LaravelValidator validator;
	private final StageOrder stageOrder;

	CatalogWriteController(Db db, CatalogReads reads, LaravelValidator validator, StageOrder stageOrder) {
		this.db = db;
		this.reads = reads;
		this.validator = validator;
		this.stageOrder = stageOrder;
	}

	// ======================================================================
	// Urun kategorileri (production_product_type.manage)
	// ======================================================================

	@PostMapping("/api/production-products")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeProduct(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("key", "required|string|max:255|unique:production_products,key")
			.field("name", "required|string|max:255|unique:production_products,name")
			.field("description", "nullable|string")
			.field("default_density_kg_per_litre", "nullable|numeric|min:0.0001|max:25"));
		return DataResponse.created(reads.product(db.create("production_products", v.all()), null, null, null, List.of()));
	}

	@RequestMapping(path = "/api/production-products/{productionProduct}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_product_type.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateProduct(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("key", "sometimes|string|max:255|unique:production_products,key," + productionProduct.id())
			.field("name", "sometimes|string|max:255|unique:production_products,name," + productionProduct.id())
			.field("description", "nullable|string")
			.field("default_density_kg_per_litre", "nullable|numeric|min:0.0001|max:25"));
		db.update("production_products", productionProduct.id(), v.all());
		return DataResponse.of(reads.product(db.find("production_products", productionProduct.id()).orElseThrow(), null, null, null, List.of()));
	}

	@DeleteMapping("/api/production-products/{productionProduct}")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	Map<String, String> destroyProduct(@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct) {
		long id = productionProduct.id();
		String mappings = "(select id from production_product_attribute_definitions where production_product_id = " + id + ")";
		DeletionGuard.of("'" + productionProduct.string("name") + "' ürün kategorisi")
			.usedBy("ürün tipi", countBy("production_product_types", "production_product_id", id))
			.usedBy("tank bakiyesi", countBy("storage_unit_balances", "production_product_id", id))
			.usedBy("tank izni", countBy("storage_unit_allowed_production_products", "production_product_id", id))
			.usedBy("hat girdisi", countBy("production_line_input_production_products", "production_product_id", id))
			.usedBy("hat çıktısı", countBy("production_line_output_production_products", "production_product_id", id))
			.usedBy("aşama extension girdisi", countBy("production_stage_extension_input_production_products", "production_product_id", id))
			.usedBy("aşama extension çıktısı", countBy("production_stage_extension_output_production_products", "production_product_id", id))
			.usedBy("materyal attribute değeri", db.count("select count(*) from material_attribute_values"
				+ " where production_product_attribute_definition_id in " + mappings))
			.usedBy("tank attribute kuralı", db.count("select count(*) from storage_unit_attribute_rules"
				+ " where production_product_attribute_definition_id in " + mappings))
			.check();
		db.delete("production_products", id);
		return Map.of("message", "ProductionProductType type deleted successfully.");
	}

	// ======================================================================
	// Urun tipleri (production_product_type.manage)
	// ======================================================================

	@PostMapping("/api/production-product-types")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeProductType(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255")
			.field("code", "required|string|max:255|unique:production_product_types,code")
			.field("type", "required|string|max:255")
			.field("unit", "required|string|max:255")
			.field("production_product_id", "required|exists:production_products,id")
			.field("density_kg_per_litre", "nullable|numeric|min:0.0001|max:25"));
		return DataResponse.created(reads.productTypes(List.of(db.create("production_product_types", v.all()))).getFirst());
	}

	@RequestMapping(path = "/api/production-product-types/{productionProductType}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_product_type.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateProductType(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("code", "sometimes|string|max:255|unique:production_product_types,code," + productionProductType.id())
			.field("type", "sometimes|string|max:255")
			.field("unit", "sometimes|string|max:255")
			.field("production_product_id", "sometimes|exists:production_products,id")
			.field("density_kg_per_litre", "nullable|numeric|min:0.0001|max:25"));
		db.update("production_product_types", productionProductType.id(), v.all());
		return DataResponse.of(reads.productTypes(List.of(db.find("production_product_types", productionProductType.id()).orElseThrow())).getFirst());
	}

	@DeleteMapping("/api/production-product-types/{productionProductType}")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	Map<String, String> destroyProductType(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		long id = productionProductType.id();
		DeletionGuard.of("'" + productionProductType.string("name") + "' ürün tipi")
			.usedBy("materyal", countBy("materials", "production_product_type_id", id))
			.usedBy("parti girdisi", countBy("production_batch_input_production_product_types", "production_product_type_id", id))
			.usedBy("parti çıktısı", countBy("production_batch_output_production_product_types", "production_product_type_id", id))
			.usedBy("tank izni", countBy("storage_unit_allowed_production_product_types", "production_product_type_id", id))
			.usedBy("tarif içeriği", countBy("finished_product_recipe_contents", "production_product_type_id", id))
			.check();
		db.delete("production_product_types", id);
		return Map.of("message", "ProductionProductType deleted successfully.");
	}

	// ======================================================================
	// Attribute eslemeleri (Laravel'de yalnizca giris; izin kontrolu yok)
	// ======================================================================

	/**
	 * Laravel'de her zaman 500 (unique kuralinda Query Builder'da olmayan whereHas). Burada amaclanan:
	 * anahtar bu kategoride zaten eslenmisse 422; global tanim yoksa olusur (firstOrCreate). Cevap 200
	 * ({@code ->fresh()}).
	 */
	@PostMapping("/api/production-products/{productionProduct}/attribute-definitions")
	@Transactional
	DataResponse<Map<String, Object>> storeMapping(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("attribute_key", "required|string|max:100")
			.field("label", "required|string|max:255")
			.field("description", "nullable|string")
			.field("data_type", "required|string|" + ATTRIBUTE_TYPES)
			.field("unit", "nullable|string|max:50")
			.field("is_required", "nullable|boolean")
			.field("sort_order", "nullable|integer|min:0"));
		assertKeyNotMapped(productionProduct.id(), v.string("attribute_key"), null);
		Row definition = db.first("select * from attribute_definitions where attribute_key = ? limit 1", v.get("attribute_key"))
			.orElseGet(() -> {
				Map<String, Object> values = new LinkedHashMap<>();
				values.put("attribute_key", v.get("attribute_key"));
				values.put("label", v.get("label"));
				values.put("data_type", v.get("data_type"));
				values.put("unit", v.get("unit"));
				values.put("description", v.get("description"));
				return db.find("attribute_definitions", db.insert("attribute_definitions", values)).orElseThrow();
			});
		Map<String, Object> mapping = new LinkedHashMap<>();
		mapping.put("attribute_definition_id", definition.id());
		mapping.put("is_required", v.get("is_required") == null ? false : v.get("is_required"));
		mapping.put("sort_order", v.get("sort_order") == null ? 0 : v.get("sort_order"));
		mapping.put("production_product_id", productionProduct.id());
		long id = db.insert("production_product_attribute_definitions", mapping);
		return DataResponse.of(reads.mappings(List.of(db.find("production_product_attribute_definitions", id).orElseThrow()), true).getFirst());
	}

	/**
	 * Laravel'de {@code attribute_key} gonderilince 500 (unique kurali eslemeler tablosunda olmayan kolona
	 * bakiyor); servis anahtari zaten degistirmiyor. Burada: bu kategoride baska eslemede varsa 422, yoksa
	 * yok sayilir. Bos (null) degerler degismez ({@code array_filter}).
	 */
	@RequestMapping(path = "/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}",
		method = {RequestMethod.PUT, RequestMethod.PATCH})
	@Transactional
	DataResponse<Map<String, Object>> updateMapping(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct,
		@Bound(table = "production_product_attribute_definitions", model = "ProductionProductAttributeDefinition") Row attributeDefinition,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("attribute_key", "sometimes|string|max:100")
			.field("label", "sometimes|string|max:255")
			.field("description", "nullable|string")
			.field("data_type", "sometimes|string|" + ATTRIBUTE_TYPES)
			.field("unit", "nullable|string|max:50")
			.field("is_required", "nullable|boolean")
			.field("sort_order", "sometimes|integer|min:0"));
		if (v.has("attribute_key")) {
			assertKeyNotMapped(productionProduct.id(), v.string("attribute_key"), attributeDefinition.id());
		}
		CatalogReadController.ensureMappingBelongs(productionProduct, attributeDefinition);
		Map<String, Object> definition = new LinkedHashMap<>();
		putIfNotNull(definition, "label", v.get("label"));
		putIfNotNull(definition, "unit", v.get("unit"));
		putIfNotNull(definition, "description", v.get("description"));
		db.update("attribute_definitions", attributeDefinition.longValue("attribute_definition_id"), definition);
		Map<String, Object> mapping = new LinkedHashMap<>();
		putIfNotNull(mapping, "is_required", v.get("is_required"));
		putIfNotNull(mapping, "sort_order", v.get("sort_order"));
		db.update("production_product_attribute_definitions", attributeDefinition.id(), mapping);
		return DataResponse.of(reads.mappings(List.of(db.find("production_product_attribute_definitions",
			attributeDefinition.id()).orElseThrow()), true).getFirst());
	}

	@DeleteMapping("/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}")
	@Transactional
	ResponseEntity<Void> destroyMapping(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct,
		@Bound(table = "production_product_attribute_definitions", model = "ProductionProductAttributeDefinition") Row attributeDefinition) {
		CatalogReadController.ensureMappingBelongs(productionProduct, attributeDefinition);
		String label = db.first("select label from attribute_definitions where id = ?", attributeDefinition.get("attribute_definition_id"))
			.map(r -> r.string("label")).orElse(null);
		DeletionGuard.of("'" + label + "' attribute eşlemesi")
			.usedBy("materyal değeri", countBy("material_attribute_values", "production_product_attribute_definition_id", attributeDefinition.id()))
			.usedBy("tank attribute kuralı", countBy("storage_unit_attribute_rules", "production_product_attribute_definition_id", attributeDefinition.id()))
			.check();
		db.delete("production_product_attribute_definitions", attributeDefinition.id());
		return ResponseEntity.noContent().build();
	}

	private void assertKeyNotMapped(long productId, String key, Long exceptMapping) {
		long existing = db.count("""
			select count(*) from production_product_attribute_definitions m
			join attribute_definitions d on d.id = m.attribute_definition_id
			where m.production_product_id = ? and d.attribute_key = ? and m.id <> ?""", productId, key, exceptMapping == null ? 0 : exceptMapping);
		if (existing > 0) {
			throw LaravelValidationException.of("attribute_key", "The attribute key has already been taken.");
		}
	}

	// ======================================================================
	// Proses tipleri (process_type.manage) ve alanlari (process_type_field.manage)
	// ======================================================================

	@PostMapping("/api/process-types")
	@RequiresPermission("process_type.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeProcessType(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255|unique:process_types,name")
			.field("description", "nullable|string")
			.field("category", "required|string|" + PROCESS_CATEGORIES)
			.field("is_active", "nullable|boolean"));
		return DataResponse.created(reads.processTypes(List.of(db.create("process_types", v.all()))).getFirst());
	}

	/** Laravel'de ignore() route('processType') ile (parametre adi process_type): kendi adiyla guncelleme 422; burada gecer. */
	@RequestMapping(path = "/api/process-types/{process_type}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("process_type.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateProcessType(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|string|max:255|unique:process_types,name," + processType.id())
			.field("description", "nullable|string")
			.field("category", "sometimes|string|" + PROCESS_CATEGORIES)
			.field("is_active", "sometimes|boolean"));
		db.update("process_types", processType.id(), v.all());
		return DataResponse.of(reads.processTypes(List.of(db.find("process_types", processType.id()).orElseThrow())).getFirst());
	}

	@DeleteMapping("/api/process-types/{process_type}")
	@RequiresPermission("process_type.manage")
	@Transactional
	Map<String, String> destroyProcessType(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType) {
		DeletionGuard.of("'" + processType.string("name") + "' proses tipi")
			.usedBy("üretim aşaması", countBy("production_stages", "process_type_id", processType.id()))
			.usedBy("girilmiş ölçüm", db.count("select count(*) from batch_process_field_values where process_type_field_id in"
				+ " (select id from process_type_fields where process_type_id = ?)", processType.id()))
			.check();
		db.delete("process_types", processType.id());
		return Map.of("message", "Process type deleted successfully.");
	}

	/**
	 * Laravel'de unique kurali route('processType') ile (null): ayni proseste tekrar eden anahtar
	 * veritabani kisitina takilip 500 veriyordu; burada 422.
	 */
	@PostMapping("/api/process-types/{process_type}/fields")
	@RequiresPermission("process_type_field.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeField(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType, RequestInput input) {
		ValidatedInput v = validator.validate(input, fieldRules(true, processType.id(), null));
		Map<String, Object> values = jsonOptions(v.all());
		values.put("process_type_id", processType.id());
		return DataResponse.created(reads.field(db.create("process_type_fields", values)));
	}

	/** Once validation (FormRequest), sonra "bu prosese ait mi" (404). */
	@RequestMapping(path = "/api/process-types/{process_type}/fields/{field}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("process_type_field.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateField(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType,
		@Bound(table = "process_type_fields", model = "ProcessTypeField") Row field, RequestInput input) {
		ValidatedInput v = validator.validate(input, fieldRules(false, processType.id(), field.id()));
		CatalogReadController.ensureFieldBelongs(processType, field);
		db.update("process_type_fields", field.id(), jsonOptions(v.all()));
		return DataResponse.of(reads.field(db.find("process_type_fields", field.id()).orElseThrow()));
	}

	@DeleteMapping("/api/process-types/{process_type}/fields/{field}")
	@RequiresPermission("process_type_field.manage")
	@Transactional
	Map<String, String> destroyField(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType,
		@Bound(table = "process_type_fields", model = "ProcessTypeField") Row field) {
		CatalogReadController.ensureFieldBelongs(processType, field);
		DeletionGuard.of("'" + field.string("label") + "' alanı")
			.usedBy("girilmiş ölçüm", countBy("batch_process_field_values", "process_type_field_id", field.id()))
			.check("Artık kullanılmayacaksa is_active alanını false yapın.");
		db.delete("process_type_fields", field.id());
		return Map.of("message", "Process type field deleted successfully.");
	}

	private static Rules fieldRules(boolean store, long processTypeId, Long fieldId) {
		String presence = store ? "required" : "sometimes";
		return Rules.of()
			.field("field_key", presence + "|string|max:255|unique:process_type_fields,field_key,"
				+ (fieldId == null ? "NULL" : fieldId) + ",id,process_type_id," + processTypeId)
			.field("label", presence + "|string|max:255")
			.field("data_type", presence + "|string|" + FIELD_TYPES)
			.field("is_required", (store ? "nullable" : "sometimes") + "|boolean")
			.field("unit", "nullable|string|max:50")
			.field("sort_order", (store ? "nullable" : "sometimes") + "|integer|min:0")
			.field("is_active", (store ? "nullable" : "sometimes") + "|boolean")
			.field("options", "nullable|array")
			.field("default_value", "nullable|string");
	}

	/** {@code 'options' => 'array'} cast'i: JSON olarak yazilir. */
	private static Map<String, Object> jsonOptions(Map<String, Object> validated) {
		Map<String, Object> values = new LinkedHashMap<>(validated);
		if (values.containsKey("options")) {
			values.put("options", Db.json(values.get("options")));
		}
		return values;
	}

	// ======================================================================
	// Hatlar (production_line.manage)
	// ======================================================================

	/** Cevap 200: {@code getById($productionLine->fresh())}. */
	@PostMapping("/api/production-lines")
	@RequiresPermission("production_line.manage")
	@Transactional
	DataResponse<Map<String, Object>> storeLine(RequestInput input) {
		ValidatedInput v = validator.validate(input, lineRules(true));
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("description", v.get("description"));
		long id = db.insert("production_lines", values);
		replaceLineProducts("production_line_input_production_products", id, items(v.get("input_production_products")));
		replaceLineProducts("production_line_output_production_products", id, items(v.get("output_production_products")));
		return DataResponse.of(reads.line(db.find("production_lines", id).orElseThrow(), CatalogReads.LineLoad.FULL));
	}

	@RequestMapping(path = "/api/production-lines/{production_line}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_line.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateLine(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line, RequestInput input) {
		ValidatedInput v = validator.validate(input, lineRules(false));
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name") != null ? v.get("name") : line.get("name"));
		values.put("description", v.has("description") ? v.get("description") : line.get("description"));
		db.update("production_lines", line.id(), values);
		if (v.has("input_production_products")) {
			replaceLineProducts("production_line_input_production_products", line.id(), items(v.get("input_production_products")));
		}
		if (v.has("output_production_products")) {
			replaceLineProducts("production_line_output_production_products", line.id(), items(v.get("output_production_products")));
		}
		return DataResponse.of(reads.line(db.find("production_lines", line.id()).orElseThrow(), CatalogReads.LineLoad.FULL));
	}

	@DeleteMapping("/api/production-lines/{production_line}")
	@RequiresPermission("production_line.manage")
	@Transactional
	Map<String, String> destroyLine(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line) {
		DeletionGuard.of("'" + line.string("name") + "' hattı")
			.usedBy("üretim partisi", countBy("production_batches", "production_line_id", line.id()))
			.check();
		db.execute("delete from production_line_input_production_products where production_line_id = ?", line.id());
		db.execute("delete from production_line_output_production_products where production_line_id = ?", line.id());
		db.delete("production_lines", line.id());
		return Map.of("message", "Production line deleted successfully.");
	}

	private static Rules lineRules(boolean store) {
		Rules rules = Rules.of()
			.field("name", (store ? "required" : "sometimes") + "|string|max:255")
			.field("description", "nullable|string");
		for (String side : List.of("input_production_products", "output_production_products")) {
			rules.field(side, (store ? "required" : "sometimes") + "|array|min:1")
				.field(side + ".*.production_product_id", (store ? "required" : "required_with:" + side)
					+ "|integer|exists:production_products,id|distinct")
				.field(side + ".*.production_product_type_id", "nullable|integer|exists:production_product_types,id")
				.field(side + ".*.sort_order", "nullable|integer|min:1");
		}
		return rules;
	}

	/** replaceInputProductTypes / replaceOutputProductTypes */
	private void replaceLineProducts(String table, long lineId, List<Map<String, Object>> items) {
		db.execute("delete from " + table + " where production_line_id = ?", lineId);
		for (int i = 0; i < items.size(); i++) {
			Map<String, Object> item = items.get(i);
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("production_product_id", item.get("production_product_id"));
			values.put("production_product_type_id", item.get("production_product_type_id"));
			values.put("sort_order", item.get("sort_order") != null ? item.get("sort_order") : i + 1);
			values.put("production_line_id", lineId);
			db.insert(table, values);
		}
	}

	// ======================================================================
	// Asamalar ve uzantilari (production_stage.manage)
	// ======================================================================

	@PostMapping("/api/production-lines/{production_line}/stages")
	@RequiresPermission("production_stage.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeStage(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("process_type_id", "nullable|integer|exists:process_types,id")
			.field("name", "required|string|max:255")
			.field("stage_order", "required|integer|min:1")
			.field("is_required", "nullable|boolean")
			.field("requires_approval", "nullable|boolean"));
		return DataResponse.created(reads.stages(List.of(stageOrder.create(line.id(), v.all())), true, false).getFirst());
	}

	@RequestMapping(path = "/api/production-lines/{production_line}/stages/{stage}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_stage.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateStage(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("process_type_id", "sometimes|nullable|integer|exists:process_types,id")
			.field("name", "sometimes|string|max:255")
			.field("stage_order", "sometimes|integer|min:1")
			.field("is_required", "sometimes|boolean")
			.field("requires_approval", "sometimes|boolean"));
		CatalogReadController.ensureStageBelongs(line, stage);
		stageOrder.update(line.id(), stage, v.all());
		return DataResponse.of(reads.stages(List.of(db.find("production_stages", stage.id()).orElseThrow()), true, false).getFirst());
	}

	@DeleteMapping("/api/production-lines/{production_line}/stages/{stage}")
	@RequiresPermission("production_stage.manage")
	@Transactional
	Map<String, String> destroyStage(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage) {
		CatalogReadController.ensureStageBelongs(line, stage);
		String name = stage.string("name") != null ? stage.string("name") : "#" + stage.id();
		DeletionGuard.of("'" + name + "' aşaması")
			.usedBy("parti prosesi", countBy("batch_processes", "production_stage_id", stage.id()))
			.check();
		stageOrder.delete(line.id(), stage);
		return Map.of("message", "Production stage deleted successfully.");
	}

	/** Laravel'de asamanin bu hatta ait oldugu kontrol edilmiyor (baska hattin asamasi da senkronlanir). */
	@PutMapping("/api/production-lines/{productionLine}/stages/{stage}/extensions")
	@RequiresPermission("production_stage.manage")
	@Transactional
	DataResponse<Map<String, Object>> syncExtensions(
		@Bound(table = "production_lines", model = "ProductionLine") Row productionLine,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("extensions", "required|array")
			.field("extensions.*.stage_extension_type_id", "required|integer|exists:stage_extension_types,id|distinct")
			.field("extensions.*.is_required", "nullable|boolean")
			.field("extensions.*.is_enabled", "nullable|boolean")
			.field("extensions.*.sort_order", "nullable|integer|min:1")
			.field("extensions.*.values", "nullable|array")
			.field("extensions.*.values.*.stage_extension_type_field_id", "nullable|integer|exists:stage_extension_type_fields,id")
			.field("extensions.*.values.*.value_boolean", "nullable|boolean")
			.field("extensions.*.values.*.field_key", "nullable|string")
			.field("extensions.*.values.*.value_integer", "nullable|integer")
			.field("extensions.*.values.*.value_decimal", "nullable|numeric")
			.field("extensions.*.values.*.value_text", "nullable|string")
			.field("extensions.*.values.*.value_json", "nullable|array")
			.field("extensions.*.input_production_product_ids", "nullable|array")
			.field("extensions.*.input_production_product_ids.*", "integer|exists:production_products,id|distinct")
			.field("extensions.*.output_production_product_ids", "nullable|array")
			.field("extensions.*.output_production_product_ids.*", "integer|exists:production_products,id|distinct"));
		new ExtensionSync(db).sync(stage.id(), items(v.get("extensions")));
		return DataResponse.of(reads.stages(List.of(db.find("production_stages", stage.id()).orElseThrow()), true, true).getFirst());
	}

	// ---- yardimcilar -------------------------------------------------------

	private long countBy(String table, String column, long id) {
		return db.count("select count(*) from " + table + " where " + column + " = ?", id);
	}

	private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> items(Object value) {
		if (value instanceof List<?> list) {
			return list.stream().map(i -> i instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
		}
		if (value instanceof Map<?, ?> map) {
			return ((Map<String, Object>) map).values().stream()
				.map(i -> i instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
		}
		return List.of();
	}

}
