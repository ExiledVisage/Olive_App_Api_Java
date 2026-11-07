package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code ProductionStageExtensionResource}: uzanti tipi (alanlariyla), degerler (alaniyla), girdi
 * ve cikti urunleri.
 */
@Component
public class ProductionStageExtensionResource {

	private final Db db;
	private final ProcessResources process;

	ProductionStageExtensionResource(Db db, ProcessResources process) {
		this.db = db;
		this.process = process;
	}

	/**
	 * Asamaya gore (sort_order sirasinda). {@code typeFieldsLoaded}: uzanti tipinin alanlari eager mi
	 * (degilse Laravel resource'ta tip basina lazy load eder, sonuc ayni).
	 */
	public Map<Long, List<Map<String, Object>>> byStage(List<Long> stageIds, boolean typeFieldsLoaded) {
		return Relations.groupItems(extensions(db.whereIn("production_stage_extensions", "production_stage_id", stageIds,
			"sort_order asc"), typeFieldsLoaded), "production_stage_id");
	}

	public List<Map<String, Object>> extensions(List<Row> extensions, boolean typeFieldsLoaded) {
		List<Long> ids = Relations.ids(extensions);
		Map<Long, Row> types = Relations.byId(db.whereIn("stage_extension_types", "id",
			Relations.column(extensions, "stage_extension_type_id"), null));
		Map<Long, List<Map<String, Object>>> typeFields = process.extensionTypeFields(new ArrayList<>(types.keySet()));

		List<Row> valueRows = db.whereIn("production_stage_extension_values", "production_stage_extension_id", ids, null);
		Map<Long, Row> valueFields = Relations.byId(db.whereIn("stage_extension_type_fields", "id",
			Relations.column(valueRows, "stage_extension_type_field_id"), null));
		Map<Long, List<Map<String, Object>>> values = new LinkedHashMap<>();
		for (Row v : valueRows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", v.get("id"));
			r.put("production_stage_extension_id", v.get("production_stage_extension_id"));
			r.put("stage_extension_type_field_id", v.get("stage_extension_type_field_id"));
			r.put("value_boolean", v.get("value_boolean"));
			r.put("value_integer", v.get("value_integer"));
			r.put("value_decimal", Casts.decimal(v.decimal("value_decimal"), 4));
			r.put("value_text", v.get("value_text"));
			r.put("value_json", v.get("value_json"));
			Row field = valueFields.get(v.longValue("stage_extension_type_field_id"));
			r.put("field", field == null ? null : process.extensionTypeField(field));
			values.computeIfAbsent(v.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(r);
		}
		Map<Long, List<Map<String, Object>>> inputs = products("production_stage_extension_input_production_products", ids);
		Map<Long, List<Map<String, Object>>> outputs = products("production_stage_extension_output_production_products", ids);

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row e : extensions) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", e.get("id"));
			r.put("production_stage_id", e.get("production_stage_id"));
			r.put("stage_extension_type_id", e.get("stage_extension_type_id"));
			r.put("is_required", e.get("is_required"));
			r.put("is_enabled", e.get("is_enabled"));
			r.put("sort_order", e.get("sort_order"));
			Row type = types.get(e.longValue("stage_extension_type_id"));
			Map<String, Object> t = new LinkedHashMap<>();
			t.put("id", type == null ? null : type.get("id"));
			t.put("key", type == null ? null : type.get("key"));
			t.put("name", type == null ? null : type.get("name"));
			t.put("description", type == null ? null : type.get("description"));
			t.put("fields", type == null ? List.of() : typeFields.getOrDefault(type.id(), List.of()));
			r.put("stage_extension_type", t);
			r.put("values", values.getOrDefault(e.id(), List.of()));
			r.put("input_production_products", inputs.getOrDefault(e.id(), List.of()));
			r.put("output_production_products", outputs.getOrDefault(e.id(), List.of()));
			out.add(r);
		}
		return out;
	}

	/** ProductionStageExtension(Input|Output)ProductionProductResource */
	private Map<Long, List<Map<String, Object>>> products(String table, List<Long> extensionIds) {
		List<Row> rows = db.whereIn(table, "production_stage_extension_id", extensionIds, "sort_order asc");
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(rows, "production_product_id"), null));
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row row : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", row.get("id"));
			r.put("production_stage_extension_id", row.get("production_stage_extension_id"));
			r.put("production_product_id", row.get("production_product_id"));
			r.put("sort_order", row.get("sort_order"));
			r.put("is_required", row.get("is_required"));
			Row product = products.get(row.longValue("production_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product == null ? null : product.get("id"));
			p.put("key", product == null ? null : product.get("key"));
			p.put("name", product == null ? null : product.get("name"));
			p.put("description", product == null ? null : product.get("description"));
			r.put("production_product", p);
			out.computeIfAbsent(row.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

}
