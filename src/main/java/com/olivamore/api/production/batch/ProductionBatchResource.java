package com.olivamore.api.production.batch;

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
import com.olivamore.api.production.batchprocess.BatchProcessResource;

/**
 * Laravel {@code ProductionBatchResource} ({@code ProductionBatchService::relations()}): hat, giris/cikis tipleri,
 * prosesler, alan degerleri, asama uzantilari, materyaller. Siralanmayan iliskiler Laravel'in sorgusuyla.
 */
@Component
class ProductionBatchResource {

	private final Db db;
	private final CatalogResources catalog;
	private final MaterialResources materials;
	private final BatchProcessResource processResource;

	ProductionBatchResource(Db db, CatalogResources catalog, MaterialResources materials, BatchProcessResource processResource) {
		this.db = db;
		this.catalog = catalog;
		this.materials = materials;
		this.processResource = processResource;
	}

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
		Map<Long, List<Map<String, Object>>> processes = group(processResource.processes(processRows, batchById), processRows, "production_batch_id");
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

	// ---- yardimcilar -------------------------------------------------------

	private Map<Long, Row> byId(String table, java.util.Collection<Row> rows, String... columns) {
		List<Long> keys = new ArrayList<>();
		for (String column : columns) {
			keys.addAll(Relations.column(rows, column));
		}
		return Relations.byId(db.whereIn(table, "id", keys, null));
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
