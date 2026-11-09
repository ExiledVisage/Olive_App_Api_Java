package com.olivamore.api.production.batchprocess;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.process.ProcessTypeFieldResponse;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.productionline.StageExtensionResponse;
import com.olivamore.api.inventory.MaterialResources;
import com.olivamore.api.inventory.MaterialResponse;
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
 * Laravel {@code BatchProcessResource} ({@code BatchProcessService::relations()}): parti baglaminda ya da detayda.
 * Parti resource'u da kullanir. Siralanmayan iliskiler (materyaller, alan degerleri) Laravel'in sorgusuyla.
 */
@Component
public class BatchProcessResource {

	private final Db db;
	private final ProcessResources process;
	private final ProductionLineResources lines;
	private final MaterialResources materials;

	BatchProcessResource(Db db, ProcessResources process, ProductionLineResources lines, MaterialResources materials) {
		this.db = db;
		this.process = process;
		this.lines = lines;
		this.materials = materials;
	}

	/**
	 * Parti baglamindaki yukleme: productionBatch, operator, productionStage.processType.fields,
	 * productionStage.extensions, fieldValues.processTypeField, created/closed materyaller.
	 * @param batches prosesin partisi zaten elimizdeyse (yoksa yuklenir)
	 */
	public List<Map<String, Object>> processes(List<Row> processes, Map<Long, Row> batches) {
		return processes(processes, batches, null);
	}

	/**
	 * Proses detayi ({@code BatchProcessService::relations()}): parti baglamina ek olarak submittedBy, approvedBy,
	 * reviews, uzanti degerleri; materyaller tum iliskileriyle. {@code requires_approval} hesaplanmis deger.
	 */
	Map<String, Object> process(Row process, boolean requiresApproval) {
		return processes(List.of(process), Map.of(), requiresApproval).getFirst();
	}

	/** @param requiresApproval null: parti baglami; degilse detay */
	private List<Map<String, Object>> processes(List<Row> processes, Map<Long, Row> batches, Boolean requiresApproval) {
		boolean detail = requiresApproval != null;
		MaterialResources.Load materialLoad = detail ? MaterialResources.Load.FULL : MaterialResources.Load.TYPE_AND_ATTRIBUTES;
		List<Long> ids = Relations.ids(processes);
		Map<Long, Row> batchById = new LinkedHashMap<>(batches);
		List<Long> missing = Relations.column(processes, "production_batch_id").stream().filter(id -> !batchById.containsKey(id)).toList();
		batchById.putAll(Relations.byId(db.whereIn("production_batches", "id", missing, null)));
		Map<Long, Row> operators = byId("users", processes, "operator_id");
		Map<Long, Row> stages = byId("production_stages", processes, "production_stage_id");
		Map<Long, Row> types = byId("process_types", stages.values(), "process_type_id");
		Map<Long, List<ProcessTypeFieldResponse>> typeFields = process.processTypeFields(new ArrayList<>(types.keySet()));
		Map<Long, List<StageExtensionResponse>> extensions = lines.stageExtensions(new ArrayList<>(stages.keySet()), detail);
		Map<Long, List<Map<String, Object>>> fieldValues = fieldValues(ids);
		Map<Long, List<Map<String, Object>>> reviews = detail ? reviews(ids) : Map.of();
		Map<Long, Row> users = detail ? byId("users", processes, "submitted_by_user_id", "approved_by_user_id") : Map.of();
		List<Row> createdRows = db.whereIn("materials", "created_by_batch_process_id", ids, null);
		Map<Long, List<MaterialResponse>> created = group(materials.materials(Relations.ids(createdRows), materialLoad),
			createdRows, "created_by_batch_process_id");
		List<Row> closedRows = db.whereIn("materials", "closed_by_batch_process_id", ids, null);
		Map<Long, List<MaterialResponse>> closed = group(materials.materials(Relations.ids(closedRows), materialLoad),
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
			r.put("requires_approval", requiresApproval);
			r.put("submitted_at", p.get("submitted_at"));
			if (detail) {
				r.put("submitted_by", nameOf(users.get(Relations.key(p.get("submitted_by_user_id")))));
			}
			r.put("approved_at", p.get("approved_at"));
			if (detail) {
				r.put("approved_by", nameOf(users.get(Relations.key(p.get("approved_by_user_id")))));
				r.put("reviews", reviews.getOrDefault(p.id(), List.of()));
			}
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

	/** reviews.reviewedBy, reviews.submittedBy (reviewed_at, id sirasiyla). */
	private Map<Long, List<Map<String, Object>>> reviews(List<Long> processIds) {
		List<Row> rows = db.whereIn("batch_process_reviews", "batch_process_id", processIds, "reviewed_at asc, id asc");
		Map<Long, Row> users = byId("users", rows, "reviewed_by_user_id", "submitted_by_user_id");
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row v : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", v.get("id"));
			r.put("decision", v.get("decision"));
			r.put("note", v.get("note"));
			r.put("operation_data", v.get("operation_data"));
			r.put("reviewed_at", v.get("reviewed_at"));
			r.put("reviewed_by", nameOf(users.get(Relations.key(v.get("reviewed_by_user_id")))));
			r.put("submitted_by", nameOf(users.get(Relations.key(v.get("submitted_by_user_id")))));
			out.computeIfAbsent(v.longValue("batch_process_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	private static Map<String, Object> nameOf(Row user) {
		return user == null ? null : pick(user, "id", "name");
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

	private Map<Long, Row> byId(String table, java.util.Collection<Row> rows, String... columns) {
		List<Long> keys = new ArrayList<>();
		for (String column : columns) {
			keys.addAll(Relations.column(rows, column));
		}
		return Relations.byId(db.whereIn(table, "id", keys, null));
	}

	/** Resource'lar ile satirlari ayni sirada eslestirip yabanci anahtara gore gruplar. */
	private static <T> Map<Long, List<T>> group(List<T> resources, List<Row> rows, String key) {
		Map<Long, List<T>> out = new LinkedHashMap<>();
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
