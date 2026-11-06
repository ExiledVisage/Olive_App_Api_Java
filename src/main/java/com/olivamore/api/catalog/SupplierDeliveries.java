package com.olivamore.api.catalog;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code SupplierService::getDeliveries}: tedarikcinin uretim partileri ve kantar tartimlari.
 * <p>
 * Uretim modulunun (Faz 6) tablolarini yalnizca okur. Uretim modulu tasininca bu sorgu orada bir
 * okuma API'sine donusebilir.
 */
final class SupplierDeliveries {

	private final Db db;

	SupplierDeliveries(Db db) {
		this.db = db;
	}

	List<Map<String, Object>> forSupplier(long supplierId) {
		List<Row> batches = db.select("select * from production_batches where supplier_id = ? order by received_at desc, id desc", supplierId);
		List<Long> batchIds = CatalogReads.ids(batches);
		Map<Long, Row> fields = CatalogReads.byId(db.whereIn("supplier_fields", "id", CatalogReads.column(batches, "supplier_field_id"), null));

		List<Row> inputs = db.whereIn("production_batch_input_production_product_types", "production_batch_id", batchIds, "sort_order asc");
		Map<Long, Row> types = CatalogReads.byId(db.whereIn("production_product_types", "id", CatalogReads.column(inputs, "production_product_type_id"), null));

		List<Row> processes = db.whereIn("batch_processes", "production_batch_id", batchIds, null);
		Map<Long, Row> stages = CatalogReads.byId(db.whereIn("production_stages", "id", CatalogReads.column(processes, "production_stage_id"), null));
		Map<Long, Row> processTypes = CatalogReads.byId(db.whereIn("process_types", "id",
			CatalogReads.column(new ArrayList<>(stages.values()), "process_type_id"), null));
		List<Row> values = db.whereIn("batch_process_field_values", "batch_process_id", CatalogReads.ids(processes), null);
		Map<Long, Row> processFields = CatalogReads.byId(db.whereIn("process_type_fields", "id", CatalogReads.column(values, "process_type_field_id"), null));

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row batch : batches) {
			Row weighIn = weighIn(batch.id(), processes, stages, processTypes);
			Map<String, Object> d = new LinkedHashMap<>();
			d.put("production_batch_id", batch.get("id"));
			d.put("batch_code", batch.get("batch_code"));
			d.put("status", batch.get("status"));
			d.put("received_at", batch.get("received_at"));
			Row field = batch.longValue("supplier_field_id") == null ? null : fields.get(batch.longValue("supplier_field_id"));
			d.put("field_code", field == null ? null : field.get("code"));
			List<Object> names = new ArrayList<>();
			for (Row input : inputs) {
				if (input.longValue("production_batch_id") == batch.id()) {
					Row type = types.get(input.longValue("production_product_type_id"));
					if (type != null && type.get("name") != null && !"".equals(type.get("name"))) {
						names.add(type.get("name"));
					}
				}
			}
			d.put("product_type_names", names);
			d.put("declared_quantity_kg", CatalogReads.decimal4(batch.get("quantity_kg")));
			Object quantity = null;
			if (weighIn != null && weighIn.get("operation_data") instanceof Map<?, ?> data) {
				quantity = data.get("quantity");
			}
			d.put("weighed_quantity_kg", quantity);
			d.put("weighed_at", weighIn == null ? null : weighIn.get("completed_at"));
			List<Map<String, Object>> weighInFields = new ArrayList<>();
			if (weighIn != null) {
				for (Row v : values) {
					if (v.longValue("batch_process_id") != weighIn.id()) {
						continue;
					}
					Row pf = processFields.get(v.longValue("process_type_field_id"));
					// value_text ?? value_decimal (decimal:4) ?? value_integer
					Object value = v.get("value_text") != null ? v.get("value_text")
						: v.get("value_decimal") != null ? CatalogReads.decimal4(v.get("value_decimal")) : v.get("value_integer");
					if (value == null) {
						continue;
					}
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("key", pf == null ? null : pf.get("field_key"));
					row.put("label", pf == null ? null : pf.get("label"));
					row.put("value", value);
					weighInFields.add(row);
				}
			}
			d.put("weigh_in_fields", weighInFields);
			out.add(d);
		}
		return out;
	}

	/** Kategorisi "weighing" olan proseslerden asama sirasi en kucuk olan (sortBy kararli). */
	private static Row weighIn(long batchId, List<Row> processes, Map<Long, Row> stages, Map<Long, Row> processTypes) {
		return processes.stream()
			.filter(p -> p.longValue("production_batch_id") == batchId)
			.filter(p -> {
				Row stage = stages.get(p.longValue("production_stage_id"));
				Row type = stage == null || stage.longValue("process_type_id") == null ? null : processTypes.get(stage.longValue("process_type_id"));
				return type != null && "weighing".equals(type.get("category"));
			})
			.min(Comparator.comparingInt(p -> stages.get(p.longValue("production_stage_id")).intValue("stage_order")))
			.orElse(null);
	}

}
