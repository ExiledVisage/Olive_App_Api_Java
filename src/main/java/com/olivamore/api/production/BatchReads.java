package com.olivamore.api.production;

import com.olivamore.api.catalog.CatalogResources;
import com.olivamore.api.inventory.MaterialResources;
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
 * ProductionBatchResource ve BatchProcessResource, Laravel'in iliski yuklemeleriyle
 * ({@code ProductionBatchService::relations()}). Siralanmayan iliskiler (prosesler, materyaller, alan
 * degerleri) Laravel'in sorgusuyla ayni bicimde, siralamasiz.
 */
@Component
class BatchReads {

	private final Db db;
	private final CatalogResources catalog;
	private final MaterialResources materials;

	BatchReads(Db db, CatalogResources catalog, MaterialResources materials) {
		this.db = db;
		this.catalog = catalog;
		this.materials = materials;
	}

	// ======================================================================
	// Partiler
	// ======================================================================

	List<Map<String, Object>> batches(List<Row> batches) {
		List<Long> ids = Relations.ids(batches);
		Map<Long, List<Map<String, Object>>> inputs = batchTypes("production_batch_input_production_product_types", ids);
		Map<Long, List<Map<String, Object>>> outputs = batchTypes("production_batch_output_production_product_types", ids);
		Map<Long, Map<String, Object>> lines = lines(Relations.column(batches, "production_line_id"));
		Map<Long, Row> users = byId("users", batches, "created_by_user_id");
		Map<Long, Row> suppliers = byId("suppliers", batches, "supplier_id");
		Map<Long, Row> fields = byId("supplier_fields", batches, "supplier_field_id");
		Map<Long, Row> batchById = Relations.byId(batches);
		List<Row> processRows = db.whereIn("batch_processes", "production_batch_id", ids, null);
		Map<Long, List<Map<String, Object>>> processes = group(processes(processRows, batchById), processRows, "production_batch_id");
		List<Row> materialRows = db.whereIn("materials", "production_batch_id", ids, null);
		Map<Long, List<Map<String, Object>>> batchMaterials = group(materials.materials(materialRows, MaterialResources.Load.FULL),
			materialRows, "production_batch_id");

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row b : batches) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", b.get("id"));
			r.put("batch_code", b.get("batch_code"));
			r.put("production_line_id", b.get("production_line_id"));
			r.put("supplier_id", b.get("supplier_id"));
			r.put("supplier_field_id", b.get("supplier_field_id"));
			Row s = suppliers.get(Relations.key(b.get("supplier_id")));
			r.put("supplier", s == null ? null : pick(s, "id", "name", "code"));
			Row f = fields.get(Relations.key(b.get("supplier_field_id")));
			r.put("supplier_field", f == null ? null : pick(f, "id", "code", "location"));
			r.put("created_by_user_id", b.get("created_by_user_id"));
			r.put("status", b.get("status"));
			r.put("quantity_kg", Casts.decimal(b.get("quantity_kg"), 4));
			r.put("received_at", b.get("received_at"));
			r.put("started_at", b.get("started_at"));
			r.put("completed_at", b.get("completed_at"));
			r.put("note", b.get("note"));
			r.put("input_production_product_types", inputs.getOrDefault(b.id(), List.of()));
			r.put("output_production_product_types", outputs.getOrDefault(b.id(), List.of()));
			Map<String, Object> line = lines.get(Relations.key(b.get("production_line_id")));
			r.put("production_line", line != null ? line : emptyLine());
			Row u = users.get(Relations.key(b.get("created_by_user_id")));
			r.put("created_by_user", u == null ? null : pick(u, "id", "name", "email"));
			r.put("processes", processes.getOrDefault(b.id(), List.of()));
			r.put("materials", batchMaterials.getOrDefault(b.id(), List.of()));
			r.put("created_at", b.get("created_at"));
			r.put("updated_at", b.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	Map<String, Object> batch(Row batch) {
		return batches(List.of(batch)).getFirst();
	}

	/** Partinin giris/cikis urun tipleri (sort_order); tipin kategorisi yuklu ama yazilmiyor. */
	private Map<Long, List<Map<String, Object>>> batchTypes(String table, List<Long> batchIds) {
		List<Row> rows = db.whereIn(table, "production_batch_id", batchIds, "sort_order asc");
		Map<Long, Row> types = byId("production_product_types", rows, "production_product_type_id");
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row row : rows) {
			Row t = types.get(Relations.key(row.get("production_product_type_id")));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", row.get("id"));
			r.put("production_batch_id", row.get("production_batch_id"));
			r.put("production_product_type_id", row.get("production_product_type_id"));
			r.put("sort_order", row.get("sort_order"));
			r.put("production_product_type", t == null ? null
				: pick(t, "id", "production_product_id", "name", "code", "type", "unit"));
			out.computeIfAbsent(row.longValue("production_batch_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	/** productionLine.(input|output)ProductionProducts.productionProduct */
	private Map<Long, Map<String, Object>> lines(List<Long> lineIds) {
		List<Row> lines = db.whereIn("production_lines", "id", lineIds, null);
		Map<Long, List<Map<String, Object>>> inputs = lineProducts("production_line_input_production_products", lineIds);
		Map<Long, List<Map<String, Object>>> outputs = lineProducts("production_line_output_production_products", lineIds);
		Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
		for (Row l : lines) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", l.get("id"));
			r.put("name", l.get("name"));
			r.put("description", l.get("description"));
			r.put("input_production_products", inputs.getOrDefault(l.id(), List.of()));
			r.put("output_production_products", outputs.getOrDefault(l.id(), List.of()));
			out.put(l.id(), r);
		}
		return out;
	}

	/** Hat silinmisse ({@code productionLine?->}) alanlar null, listeler bos. */
	private static Map<String, Object> emptyLine() {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", null);
		r.put("name", null);
		r.put("description", null);
		r.put("input_production_products", List.of());
		r.put("output_production_products", List.of());
		return r;
	}

	private Map<Long, List<Map<String, Object>>> lineProducts(String table, List<Long> lineIds) {
		List<Row> rows = db.whereIn(table, "production_line_id", lineIds, "sort_order asc");
		Map<Long, Row> products = byId("production_products", rows, "production_product_id");
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row row : rows) {
			Row p = products.get(Relations.key(row.get("production_product_id")));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", row.get("id"));
			r.put("production_line_id", row.get("production_line_id"));
			r.put("production_product_id", row.get("production_product_id"));
			r.put("sort_order", row.get("sort_order"));
			r.put("production_product", p == null ? null : pick(p, "id", "key", "name", "description"));
			out.computeIfAbsent(row.longValue("production_line_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	// ======================================================================
	// Prosesler (BatchProcessResource)
	// ======================================================================

	/**
	 * Parti baglamindaki yukleme: productionBatch, operator, productionStage.processType.fields,
	 * productionStage.extensions, fieldValues.processTypeField, created/closed materyaller.
	 * @param batches prosesin partisi zaten elimizdeyse (yoksa yuklenir)
	 */
	List<Map<String, Object>> processes(List<Row> processes, Map<Long, Row> batches) {
		List<Long> ids = Relations.ids(processes);
		Map<Long, Row> batchById = new LinkedHashMap<>(batches);
		List<Long> missing = Relations.column(processes, "production_batch_id").stream().filter(id -> !batchById.containsKey(id)).toList();
		batchById.putAll(Relations.byId(db.whereIn("production_batches", "id", missing, null)));
		Map<Long, Row> operators = byId("users", processes, "operator_id");
		Map<Long, Row> stages = byId("production_stages", processes, "production_stage_id");
		Map<Long, Row> types = byId("process_types", stages.values(), "process_type_id");
		Map<Long, List<Map<String, Object>>> typeFields = catalog.processTypeFields(new ArrayList<>(types.keySet()));
		Map<Long, List<Map<String, Object>>> extensions = catalog.stageExtensions(new ArrayList<>(stages.keySet()));
		Map<Long, List<Map<String, Object>>> fieldValues = fieldValues(ids);
		List<Row> createdRows = db.whereIn("materials", "created_by_batch_process_id", ids, null);
		Map<Long, List<Map<String, Object>>> created = group(materials.materials(createdRows, MaterialResources.Load.TYPE_AND_ATTRIBUTES),
			createdRows, "created_by_batch_process_id");
		List<Row> closedRows = db.whereIn("materials", "closed_by_batch_process_id", ids, null);
		Map<Long, List<Map<String, Object>>> closed = group(materials.materials(closedRows, MaterialResources.Load.TYPE_AND_ATTRIBUTES),
			closedRows, "closed_by_batch_process_id");

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row p : processes) {
			Row stage = stages.get(Relations.key(p.get("production_stage_id")));
			Row type = stage == null ? null : types.get(Relations.key(stage.get("process_type_id")));
			Row batch = batchById.get(Relations.key(p.get("production_batch_id")));
			Row operator = operators.get(Relations.key(p.get("operator_id")));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", p.get("id"));
			r.put("production_batch_id", p.get("production_batch_id"));
			r.put("production_stage_id", p.get("production_stage_id"));
			r.put("operator_id", p.get("operator_id"));
			r.put("status", p.get("status"));
			r.put("started_at", p.get("started_at"));
			r.put("completed_at", p.get("completed_at"));
			r.put("note", p.get("note"));
			r.put("operation_data", p.get("operation_data"));
			r.put("requires_approval", null);
			r.put("submitted_at", p.get("submitted_at"));
			r.put("approved_at", p.get("approved_at"));
			Map<String, Object> b = new LinkedHashMap<>();
			b.put("id", batch == null ? null : batch.get("id"));
			b.put("batch_code", batch == null ? null : batch.get("batch_code"));
			b.put("status", batch == null ? null : batch.get("status"));
			r.put("production_batch", b);
			r.put("operator", operator == null ? null : pick(operator, "id", "name", "email"));
			Map<String, Object> st = new LinkedHashMap<>();
			st.put("id", stage == null ? null : stage.get("id"));
			st.put("name", stage == null ? null : stage.get("name"));
			st.put("stage_order", stage == null ? null : Casts.integer(stage.get("stage_order")));
			st.put("is_required", stage != null && Boolean.TRUE.equals(Casts.bool(stage.get("is_required"))));
			r.put("production_stage", st);
			r.put("process_type", type == null ? null : pick(type, "id", "name", "description", "category"));
			r.put("field_definitions", type == null ? List.of() : typeFields.getOrDefault(type.id(), List.of()));
			r.put("extensions", stage == null ? List.of() : extensions.getOrDefault(stage.id(), List.of()));
			r.put("field_values", fieldValues.getOrDefault(p.id(), List.of()));
			r.put("created_materials", created.getOrDefault(p.id(), List.of()));
			r.put("closed_materials", closed.getOrDefault(p.id(), List.of()));
			r.put("created_at", p.get("created_at"));
			r.put("updated_at", p.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** BatchProcessFieldValueResource (processTypeField yuklu), prosese gore. */
	private Map<Long, List<Map<String, Object>>> fieldValues(List<Long> processIds) {
		List<Row> rows = db.whereIn("batch_process_field_values", "batch_process_id", processIds, null);
		Map<Long, Row> fields = byId("process_type_fields", rows, "process_type_field_id");
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row v : rows) {
			Object bool = Casts.bool(v.get("value_boolean"));
			Object integer = Casts.integer(v.get("value_integer"));
			Object decimal = Casts.decimal(v.get("value_decimal"), 4);
			Object resolved = bool != null ? bool : integer != null ? integer : decimal != null ? decimal
				: v.get("value_datetime") != null ? v.get("value_datetime") : v.get("value_json") != null ? v.get("value_json")
				: v.get("value_text");
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", v.get("id"));
			r.put("batch_process_id", v.get("batch_process_id"));
			r.put("process_type_field_id", v.get("process_type_field_id"));
			r.put("value_text", v.get("value_text"));
			r.put("value_decimal", decimal);
			r.put("value_integer", integer);
			r.put("value_boolean", bool);
			r.put("value_datetime", v.get("value_datetime"));
			r.put("value_json", v.get("value_json"));
			r.put("resolved_value", resolved);
			Row f = fields.get(Relations.key(v.get("process_type_field_id")));
			if (f != null) {
				Map<String, Object> field = new LinkedHashMap<>();
				field.put("id", f.get("id"));
				field.put("process_type_id", f.get("process_type_id"));
				field.put("field_key", f.get("field_key"));
				field.put("label", f.get("label"));
				field.put("data_type", f.get("data_type"));
				field.put("is_required", Casts.bool(f.get("is_required")));
				field.put("unit", f.get("unit"));
				field.put("sort_order", f.get("sort_order"));
				field.put("is_active", Casts.bool(f.get("is_active")));
				r.put("process_type_field", field);
			}
			out.computeIfAbsent(v.longValue("batch_process_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	// ---- yardimcilar -------------------------------------------------------

	private Map<Long, Row> byId(String table, java.util.Collection<Row> rows, String column) {
		return Relations.byId(db.whereIn(table, "id", Relations.column(rows, column), null));
	}

	/** Resource'lar ile satirlari ayni sirada eslestirip yabanci anahtara gore gruplar. */
	private static Map<Long, List<Map<String, Object>>> group(List<Map<String, Object>> resources, List<Row> rows, String key) {
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (int i = 0; i < rows.size(); i++) {
			out.computeIfAbsent(Relations.key(rows.get(i).get(key)), k -> new ArrayList<>()).add(resources.get(i));
		}
		return out;
	}

	private static Map<String, Object> pick(Row row, String... columns) {
		Map<String, Object> r = new LinkedHashMap<>();
		for (String c : columns) {
			r.put(c, row.get(c));
		}
		return r;
	}

}
