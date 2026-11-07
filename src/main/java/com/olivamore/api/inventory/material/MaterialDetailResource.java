package com.olivamore.api.inventory.material;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.olivamore.api.inventory.storagemovement.StorageMovementResource;
import org.springframework.stereotype.Component;

/**
 * Laravel {@code MaterialDetailResource}: miktar dokumu (giren/ayrilan/kapanan/kalan), ebeveyn ve cocuklar, attribute
 * degerleri (tanim sirasiyla), son 20 hareket, bu materyalden cekilen dolum kosulari.
 */
@Component
class MaterialDetailResource {

	static final int MOVEMENT_LIMIT = 20;

	private final Db db;

	MaterialDetailResource(Db db) {
		this.db = db;
	}

	Map<String, Object> of(Row m) {
		Row type = m.get("production_product_type_id") == null ? null
			: db.find("production_product_types", Relations.key(m.get("production_product_type_id"))).orElse(null);
		Row batch = m.get("production_batch_id") == null ? null
			: db.find("production_batches", Relations.key(m.get("production_batch_id"))).orElse(null);
		Row parent = m.get("parent_material_id") == null ? null
			: db.find("materials", Relations.key(m.get("parent_material_id"))).orElse(null);
		List<Row> children = db.whereIn("materials", "parent_material_id", List.of(m.id()), null);
		Map<Long, Row> lineageTypes = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(parent == null ? children : concat(List.of(parent), children), "production_product_type_id"), null));
		Map<Long, Row> units = Relations.byId(db.whereIn("storage_units", "id", Relations.column(List.of(m), "current_location_id"), null));

		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", m.get("id"));
		r.put("quantity", Casts.decimal(m.get("quantity"), 4));
		r.put("unit", m.get("unit"));
		r.put("status", m.get("status"));
		r.put("origin_type", m.get("origin_type"));
		r.put("note", m.get("note"));
		r.put("created_at", m.get("created_at"));
		r.put("quantity_breakdown", quantityBreakdown(m, children));
		Row product = type == null || type.get("production_product_id") == null ? null
			: db.find("production_products", Relations.key(type.get("production_product_id"))).orElse(null);
		r.put("production_product_type", type == null ? null : typeSummary(type, product));
		if (batch == null) {
			r.put("production_batch", null);
		}
		else {
			Map<String, Object> b = new LinkedHashMap<>();
			b.put("id", batch.get("id"));
			b.put("batch_code", batch.get("batch_code"));
			b.put("status", batch.get("status"));
			r.put("production_batch", b);
		}
		r.put("storage_unit", storageUnit(m, units));
		r.put("parent_material", parent == null ? null : lineageSummary(parent, lineageTypes));
		r.put("child_materials", children.stream().map(c -> lineageSummary(c, lineageTypes)).toList());
		r.put("attribute_values", attributeValues(m.id()));
		r.put("movements", movements(m.id()));
		r.put("runs", runs(m.id()));
		return r;
	}

	// ---- detay parcalari ---------------------------------------------------

	/**
	 * Material::quantityBreakdown(): kendi miktari + ayni tipteki cocuklara ayrilan = giren;
	 * aktifse kalan, degilse kapanan.
	 */
	private static Map<String, Object> quantityBreakdown(Row m, List<Row> children) {
		double own = Php.round(Php.floatval(Casts.decimal(m.get("quantity"), 4)), 4);
		double sum = 0;
		for (Row c : children) {
			if (Objects.equals(Relations.key(c.get("production_product_type_id")), Relations.key(m.get("production_product_type_id")))) {
				sum += Php.floatval(Casts.decimal(c.get("quantity"), 4));
			}
		}
		double splitOut = Php.round(sum, 4);
		boolean active = "active".equals(m.get("status"));
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("initial", Php.jsonFloat(Php.round(own + splitOut, 4)));
		r.put("split_out", Php.jsonFloat(splitOut));
		r.put("closed", Php.jsonFloat(active ? 0.0 : own));
		r.put("remaining", Php.jsonFloat(active ? own : 0.0));
		return r;
	}

	/** Etiket, birim ve ilk dolu deger (decimal > integer > text > boolean); tanimin sort_order'ina gore (kararli). */
	private List<Map<String, Object>> attributeValues(long materialId) {
		List<Row> values = db.whereIn("material_attribute_values", "material_id", List.of(materialId), null);
		Map<Long, Row> mappings = Relations.byId(db.whereIn("production_product_attribute_definitions", "id",
			Relations.column(values, "production_product_attribute_definition_id"), null));
		Map<Long, Row> attributes = Relations.byId(db.whereIn("attribute_definitions", "id",
			Relations.column(mappings.values(), "attribute_definition_id"), null));
		List<Row> sorted = new ArrayList<>(values);
		// sortBy: null sort_order (tanim yok) en basta
		sorted.sort(Comparator.comparing((Row v) -> {
			Row mapping = mappings.get(Relations.key(v.get("production_product_attribute_definition_id")));
			return mapping == null || mapping.get("sort_order") == null ? Long.MIN_VALUE : Php.intval(mapping.get("sort_order"));
		}));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row v : sorted) {
			Row mapping = mappings.get(Relations.key(v.get("production_product_attribute_definition_id")));
			Row attribute = mapping == null ? null : attributes.get(Relations.key(mapping.get("attribute_definition_id")));
			Object value = Casts.decimal(v.get("value_decimal"), 4);
			if (value == null) {
				value = Casts.integer(v.get("value_integer"));
			}
			if (value == null) {
				value = v.get("value_text");
			}
			if (value == null) {
				value = Casts.bool(v.get("value_boolean"));
			}
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("label", attribute == null ? null : attribute.get("label"));
			r.put("unit", attribute == null ? null : attribute.get("unit"));
			r.put("value", value);
			out.add(r);
		}
		return out;
	}

	/** Son 20 hareket (moved_at desc, id desc). */
	private List<Map<String, Object>> movements(long materialId) {
		List<Row> movements = db.select("select * from storage_movements where storage_movements.material_id = ?"
			+ " order by moved_at desc, id desc limit " + MOVEMENT_LIMIT, materialId);
		List<Long> unitIds = new ArrayList<>(Relations.column(movements, "storage_unit_id"));
		unitIds.addAll(Relations.column(movements, "counterparty_storage_unit_id"));
		Map<Long, Row> units = Relations.byId(db.whereIn("storage_units", "id", unitIds, null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row mv : movements) {
			Row unit = units.get(Relations.key(mv.get("storage_unit_id")));
			Row counterparty = units.get(Relations.key(mv.get("counterparty_storage_unit_id")));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", mv.get("id"));
			r.put("finished_product_run_id", mv.get("finished_product_run_id"));
			r.put("movement_type", mv.get("movement_type"));
			r.put("quantity", Casts.decimal(mv.get("quantity"), 2));
			r.put("unit", mv.get("unit"));
			r.put("moved_at", mv.get("moved_at"));
			r.put("storage_unit_name", unit == null ? null : unit.get("name"));
			r.put("counterparty_storage_unit_name", counterparty == null ? null : counterparty.get("name"));
			out.add(r);
		}
		return out;
	}

	/** Bu materyalden cekilen dolum kosulari (created_at desc); kullanilan miktar bu materyalinki. */
	private List<Map<String, Object>> runs(long materialId) {
		List<Row> runs = db.select("select * from finished_product_runs where exists (select * from finished_product_run_materials"
			+ " where finished_product_runs.id = finished_product_run_materials.finished_product_run_id"
			+ " and source_material_id = ?) order by created_at desc", materialId);
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(runs, "finished_product_id"), null));
		Map<Long, List<Row>> used = new LinkedHashMap<>();
		if (!runs.isEmpty()) {
			used = Relations.groupBy(db.select("select * from finished_product_run_materials where finished_product_run_materials.finished_product_run_id in ("
				+ Relations.inList(Relations.ids(runs)) + ") and source_material_id = ?", materialId), "finished_product_run_id");
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row run : runs) {
			double quantity = 0;
			for (Row u : used.getOrDefault(run.id(), List.of())) {
				quantity += Php.floatval(Casts.decimal(u.get("quantity_used"), 4));
			}
			Row product = products.get(Relations.key(run.get("finished_product_id")));
			Object date = run.get("completed_at") != null ? run.get("completed_at")
				: run.get("started_at") != null ? run.get("started_at") : run.get("created_at");
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", run.get("id"));
			r.put("status", run.get("status"));
			r.put("finished_product_name", product == null ? null : product.get("name"));
			r.put("bottle_quantity", run.get("quantity"));
			r.put("quantity_used", Php.jsonFloat(quantity));
			r.put("date", date);
			out.add(r);
		}
		return out;
	}

	/** MaterialLineageService::lineageRole: parent varsa derived, yoksa origin_type'tan. */
	static String lineageRole(Row m) {
		if (m.get("parent_material_id") != null) {
			return "derived";
		}
		return switch (Php.strval(m.get("origin_type"))) {
			case "BATCH_PROCESS" -> "root_raw";
			case "STORAGE_OPENING" -> "root_opening";
			case "STORAGE_ADJUSTMENT" -> "root_adjustment";
			default -> "root_other";
		};
	}

	/** {id, name, unit, density_kg_per_litre (etkin)} */
	static Map<String, Object> typeSummary(Row type, Row product) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", type.get("id"));
		r.put("name", type.get("name"));
		r.put("unit", type.get("unit"));
		r.put("density_kg_per_litre", MaterialResource.effectiveDensity(type, product));
		return r;
	}

	/** Materyal tankta ise {id, name, code}, degilse null. */
	static Map<String, Object> storageUnit(Row m, Map<Long, Row> units) {
		Row unit = "storage_unit".equals(m.get("current_location_type")) ? units.get(Relations.key(m.get("current_location_id"))) : null;
		return unit == null ? null : StorageMovementResource.unitSummary(unit);
	}

	private static Map<String, Object> lineageSummary(Row m, Map<Long, Row> types) {
		Row type = types.get(Relations.key(m.get("production_product_type_id")));
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", m.get("id"));
		r.put("name", type == null ? null : type.get("name"));
		r.put("quantity", Casts.decimal(m.get("quantity"), 4));
		r.put("unit", m.get("unit"));
		r.put("status", m.get("status"));
		return r;
	}

	private static List<Row> concat(List<Row> a, List<Row> b) {
		List<Row> out = new ArrayList<>(a);
		out.addAll(b);
		return out;
	}

}
