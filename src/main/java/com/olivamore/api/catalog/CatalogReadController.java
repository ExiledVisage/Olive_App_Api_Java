package com.olivamore.api.catalog;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Katalogun GET endpoint'leri. Laravel'deki sorgular (siralar dahil) her metodun basinda.
 */
@RestController
class CatalogReadController {

	static final String STAGE_NOT_IN_LINE = "Stage not found for this production line.";
	static final String FIELD_NOT_IN_TYPE = "Field not found for this process type.";
	static final String MAPPING_NOT_IN_PRODUCT = "Seçilen attribute definition bu ürün kategorisine ait değil.";

	private final Db db;
	private final CatalogReads reads;

	CatalogReadController(Db db, CatalogReads reads) {
		this.db = db;
		this.reads = reads;
	}

	// ---- urun tipleri (production_product_type.view) -----------------------

	@GetMapping("/api/production-product-types")
	@RequiresPermission("production_product_type.view")
	ListResponse<Map<String, Object>> productTypes() {
		return new ListResponse.Plain<>(reads.productTypes(db.select("select * from production_product_types order by created_at desc")));
	}

	@GetMapping("/api/production-product-types/{productionProductType}")
	@RequiresPermission("production_product_type.view")
	DataResponse<Map<String, Object>> productType(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		return DataResponse.of(reads.productTypes(List.of(productionProductType)).getFirst());
	}

	/** Tipin kategorisini (tip ozelinde ya da tip belirtmeden) girdi ya da cikti olarak kullanan hatlar. */
	@GetMapping("/api/production-product-types/{productionProductType}/production-lines")
	@RequiresPermission("production_product_type.view")
	ListResponse<Map<String, Object>> productTypeLines(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		String input = matches("production_line_input_production_products", productionProductType);
		String output = matches("production_line_output_production_products", productionProductType);
		List<Row> lines = db.select("select * from production_lines where (" + input + " or " + output + ")");
		return new ListResponse.Plain<>(reads.lines(lines, CatalogReads.LineLoad.BASIC));
	}

	@GetMapping("/api/production-product-types/{productionProductType}/flow")
	@RequiresPermission("production_product_type.view")
	DataResponse<Map<String, Object>> productTypeFlow(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		List<Map<String, Object>> inputs = reads.lines(db.select("select * from production_lines where "
			+ matches("production_line_input_production_products", productionProductType)), CatalogReads.LineLoad.BASIC);
		List<Map<String, Object>> outputs = reads.lines(db.select("select * from production_lines where "
			+ matches("production_line_output_production_products", productionProductType)), CatalogReads.LineLoad.BASIC);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("production_product_type", reads.productTypes(List.of(productionProductType)).getFirst());
		data.put("input_production_lines", inputs);
		data.put("output_production_lines", outputs);
		data.put("production_lines", CatalogReads.mergeLines(inputs, outputs));
		return DataResponse.of(data);
	}

	/** ProductionProductTypeService::matchesProductType (whereHas): kategori ayni ve tip bos ya da ayni. */
	private static String matches(String pivot, Row type) {
		long category = type.longValue("production_product_id");
		long id = type.id();
		return "exists (select * from " + pivot + " where production_lines.id = " + pivot + ".production_line_id"
			+ " and production_product_id = " + category
			+ " and (production_product_type_id is null or production_product_type_id = " + id + "))";
	}

	// ---- urun kategorileri (production_product_type.view) ------------------

	@GetMapping("/api/production-products")
	@RequiresPermission("production_product_type.view")
	ListResponse<Map<String, Object>> products() {
		List<Row> products = db.select("""
			select production_products.*, (select count(*) from production_product_types
			  where production_products.id = production_product_types.production_product_id) as production_product_types_count
			from production_products order by created_at desc""");
		return new ListResponse.Plain<>(products.stream()
			.map(p -> reads.product(p, p.longValue("production_product_types_count"), null, null, List.of())).toList());
	}

	@GetMapping("/api/production-products/{productionProduct}")
	@RequiresPermission("production_product_type.view")
	DataResponse<Map<String, Object>> product(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct) {
		long id = productionProduct.id();
		List<Map<String, Object>> mappings = reads.mappings(db.whereIn("production_product_attribute_definitions",
			"production_product_id", List.of(id), null), false);
		List<Map<String, Object>> types = reads.productTypes(db.whereIn("production_product_types", "production_product_id",
			List.of(id), null));
		long count = db.count("select count(*) from production_product_types where production_product_id = ?", id);
		return DataResponse.of(reads.product(productionProduct, count, mappings, types, reads.productLines(id)));
	}

	// ---- attribute tanimlari -----------------------------------------------

	@GetMapping("/api/attribute-definitions")
	@RequiresPermission("attribute_definition.view")
	ListResponse<Map<String, Object>> attributeDefinitions() {
		return new ListResponse.Plain<>(db.select("select * from attribute_definitions order by label asc").stream()
			.map(reads::attributeDefinition).toList());
	}

	/** Laravel'de yalnizca giris ister (izin kontrolu yok). */
	@GetMapping("/api/production-products/{productionProduct}/attribute-definitions")
	ListResponse<Map<String, Object>> productMappings(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct) {
		return new ListResponse.Plain<>(reads.mappings(db.select("""
			select * from production_product_attribute_definitions
			where production_product_attribute_definitions.production_product_id = ?
			  and production_product_attribute_definitions.production_product_id is not null
			order by sort_order asc, id asc""", productionProduct.id()), true));
	}

	@GetMapping("/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}")
	DataResponse<Map<String, Object>> productMapping(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct,
		@Bound(table = "production_product_attribute_definitions", model = "ProductionProductAttributeDefinition") Row attributeDefinition) {
		ensureMappingBelongs(productionProduct, attributeDefinition);
		return DataResponse.of(reads.mappings(List.of(attributeDefinition), true).getFirst());
	}

	static void ensureMappingBelongs(Row product, Row mapping) {
		if (mapping.longValue("production_product_id") != product.id()) {
			throw new BusinessRuleException(MAPPING_NOT_IN_PRODUCT);
		}
	}

	// ---- hatlar (production_line.view) -------------------------------------

	@GetMapping("/api/production-lines")
	@RequiresPermission("production_line.view")
	ListResponse<Map<String, Object>> lines() {
		return new ListResponse.Plain<>(reads.lines(db.select("select * from production_lines order by created_at desc"),
			CatalogReads.LineLoad.FULL));
	}

	@GetMapping("/api/production-lines/{production_line}")
	@RequiresPermission("production_line.view")
	DataResponse<Map<String, Object>> line(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line) {
		return DataResponse.of(reads.line(line, CatalogReads.LineLoad.FULL));
	}

	// ---- proses tipleri ----------------------------------------------------

	@GetMapping("/api/process-types")
	@RequiresPermission("process_type.manage")
	ListResponse<Map<String, Object>> processTypes() {
		return new ListResponse.Plain<>(reads.processTypes(db.select("select * from process_types order by created_at desc")));
	}

	@GetMapping("/api/process-types/{process_type}")
	@RequiresPermission("process_type.manage")
	DataResponse<Map<String, Object>> processType(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType) {
		return DataResponse.of(reads.processTypes(List.of(processType)).getFirst());
	}

	@GetMapping("/api/process-types/{process_type}/fields")
	@RequiresPermission("process_type_field.manage")
	ListResponse<Map<String, Object>> fields(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType) {
		return new ListResponse.Plain<>(reads.fields(db.select("""
			select * from process_type_fields
			where process_type_fields.process_type_id = ? and process_type_fields.process_type_id is not null
			order by sort_order asc, sort_order asc, id asc""", processType.id())));
	}

	@GetMapping("/api/process-types/{process_type}/fields/{field}")
	@RequiresPermission("process_type_field.manage")
	DataResponse<Map<String, Object>> field(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType,
		@Bound(table = "process_type_fields", model = "ProcessTypeField") Row field) {
		ensureFieldBelongs(processType, field);
		return DataResponse.of(reads.field(field));
	}

	static void ensureFieldBelongs(Row processType, Row field) {
		if (field.longValue("process_type_id") != processType.id()) {
			throw new HttpStatusException(404, FIELD_NOT_IN_TYPE);
		}
	}

	// ---- asamalar ve uzanti tipleri (production_stage.manage) --------------

	@GetMapping("/api/production-lines/{production_line}/stages")
	@RequiresPermission("production_stage.manage")
	ListResponse<Map<String, Object>> stages(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line) {
		return new ListResponse.Plain<>(reads.stages(db.select("""
			select * from production_stages
			where production_stages.production_line_id = ? and production_stages.production_line_id is not null
			order by stage_order asc, stage_order asc""", line.id()), true, false));
	}

	@GetMapping("/api/production-lines/{production_line}/stages/{stage}")
	@RequiresPermission("production_stage.manage")
	DataResponse<Map<String, Object>> stage(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage) {
		ensureStageBelongs(line, stage);
		return DataResponse.of(reads.stages(List.of(stage), true, false).getFirst());
	}

	static void ensureStageBelongs(Row line, Row stage) {
		if (stage.longValue("production_line_id") != line.id()) {
			throw new HttpStatusException(404, STAGE_NOT_IN_LINE);
		}
	}

	@GetMapping("/api/stage-extension-types")
	@RequiresPermission("production_stage.manage")
	ListResponse<Map<String, Object>> extensionTypes() {
		return new ListResponse.Plain<>(reads.extensionTypes(db.select("select * from stage_extension_types order by name asc")));
	}

}
