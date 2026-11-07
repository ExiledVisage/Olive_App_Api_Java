package com.olivamore.api.inventory.material;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code MaterialResource} ve {@code MaterialAttributeValueResource}: hangi iliskiler yukluyse o alanlar.
 * Tank, stok hareketi ve (disa acik {@code MaterialResources} ile) uretim kullanir.
 */
@Component
public class MaterialResource {

	private final Db db;

	MaterialResource(Db db) {
		this.db = db;
	}

	/** MaterialResource'ta hangi iliskiler yuklu. */
	public record MaterialLoad(boolean type, boolean attributeValues, boolean batch, boolean parent, boolean createdBy,
		boolean closedBy) {

		/** productionProductType.productionProduct + attributeValues.definition */
		public static final MaterialLoad TYPE_AND_ATTRIBUTES = new MaterialLoad(true, true, false, false, false, false);

		/** + productionBatch, parentMaterial, createdByBatchProcess, closedByBatchProcess (ProductionBatchService) */
		public static final MaterialLoad FULL = new MaterialLoad(true, true, true, true, true, true);

	}

	public List<Map<String, Object>> materials(List<Row> materials, MaterialLoad load) {
		Map<Long, Row> types = Map.of();
		Map<Long, Row> products = Map.of();
		if (load.type()) {
			types = Relations.byId(db.whereIn("production_product_types", "id", Relations.column(materials, "production_product_type_id"), null));
			products = Relations.byId(db.whereIn("production_products", "id", Relations.column(types.values(), "production_product_id"), null));
		}
		Map<Long, List<Map<String, Object>>> values = Map.of();
		if (load.attributeValues()) {
			values = attributeValues(Relations.ids(materials));
		}
		Map<Long, Row> batches = load.batch()
			? Relations.byId(db.whereIn("production_batches", "id", Relations.column(materials, "production_batch_id"), null)) : Map.of();
		Map<Long, Row> parents = load.parent()
			? Relations.byId(db.whereIn("materials", "id", Relations.column(materials, "parent_material_id"), null)) : Map.of();
		Map<Long, Row> creators = load.createdBy()
			? Relations.byId(db.whereIn("batch_processes", "id", Relations.column(materials, "created_by_batch_process_id"), null)) : Map.of();
		Map<Long, Row> closers = load.closedBy()
			? Relations.byId(db.whereIn("batch_processes", "id", Relations.column(materials, "closed_by_batch_process_id"), null)) : Map.of();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row m : materials) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", m.get("id"));
			r.put("production_batch_id", m.get("production_batch_id"));
			r.put("production_product_type_id", m.get("production_product_type_id"));
			r.put("parent_material_id", m.get("parent_material_id"));
			r.put("origin_type", m.get("origin_type"));
			r.put("quantity", Casts.decimal(m.get("quantity"), 4));
			r.put("unit", m.get("unit"));
			r.put("status", m.get("status"));
			r.put("current_location_type", m.get("current_location_type"));
			r.put("current_location_id", m.get("current_location_id"));
			r.put("source_production_stage_id", m.get("source_production_stage_id"));
			r.put("created_by_batch_process_id", m.get("created_by_batch_process_id"));
			r.put("closed_by_batch_process_id", m.get("closed_by_batch_process_id"));
			r.put("note", m.get("note"));
			if (load.type()) {
				Row t = types.get(Relations.key(m.get("production_product_type_id")));
				r.put("production_product_type", t == null ? null : materialType(t, products.get(Relations.key(t.get("production_product_id")))));
			}
			if (load.batch()) {
				Row b = batches.get(Relations.key(m.get("production_batch_id")));
				Map<String, Object> batch = null;
				if (b != null) {
					batch = new LinkedHashMap<>();
					batch.put("id", b.get("id"));
					batch.put("batch_code", b.get("batch_code"));
					batch.put("status", b.get("status"));
				}
				r.put("production_batch", batch);
			}
			if (load.parent()) {
				Row p = parents.get(Relations.key(m.get("parent_material_id")));
				Map<String, Object> parent = null;
				if (p != null) {
					parent = new LinkedHashMap<>();
					parent.put("id", p.get("id"));
					parent.put("production_product_type_id", p.get("production_product_type_id"));
					parent.put("quantity", Casts.decimal(p.get("quantity"), 4));
					parent.put("unit", p.get("unit"));
					parent.put("status", p.get("status"));
				}
				r.put("parent_material", parent);
			}
			if (load.attributeValues()) {
				r.put("attribute_values", values.getOrDefault(m.id(), List.of()));
			}
			if (load.createdBy()) {
				Row p = creators.get(Relations.key(m.get("created_by_batch_process_id")));
				r.put("created_by_batch_process", p == null ? null : batchProcess(p));
			}
			if (load.closedBy()) {
				Row p = closers.get(Relations.key(m.get("closed_by_batch_process_id")));
				r.put("closed_by_batch_process", p == null ? null : batchProcess(p));
			}
			r.put("created_at", m.get("created_at"));
			r.put("updated_at", m.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** MaterialResource icindeki production_product_type (etkin yogunlukla). */
	private static Map<String, Object> materialType(Row t, Row product) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", t.get("id"));
		r.put("production_product_id", t.get("production_product_id"));
		r.put("name", t.get("name"));
		r.put("code", t.get("code"));
		r.put("type", t.get("type"));
		r.put("unit", t.get("unit"));
		r.put("density_kg_per_litre", effectiveDensity(t, product));
		r.put("production_product", product(product, true));
		return r;
	}

	/** ProductionProductType::effectiveDensity(): kendi > 0 ise o, yoksa kategorinin varsayilani > 0 ise o. */
	public static Number effectiveDensity(Row type, Row product) {
		BigDecimal own = Casts.toDecimal(type.get("density_kg_per_litre"));
		if (own != null && own.signum() > 0) {
			return Php.jsonFloat(own.doubleValue());
		}
		BigDecimal fallback = product == null ? null : Casts.toDecimal(product.get("default_density_kg_per_litre"));
		return fallback != null && fallback.signum() > 0 ? Php.jsonFloat(fallback.doubleValue()) : null;
	}

	/** MaterialAttributeValueResource (definition yuklu), materyale gore gruplu. */
	private Map<Long, List<Map<String, Object>>> attributeValues(List<Long> materialIds) {
		List<Row> rows = db.whereIn("material_attribute_values", "material_id", materialIds, null);
		Map<Long, Map<String, Object>> definitions = definitions(Relations.column(rows, "production_product_attribute_definition_id"));
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row v : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", v.get("id"));
			r.put("material_id", v.get("material_id"));
			r.put("production_product_attribute_definition_id", v.get("production_product_attribute_definition_id"));
			r.put("value_boolean", Casts.bool(v.get("value_boolean")));
			r.put("value_integer", Casts.integer(v.get("value_integer")));
			r.put("value_decimal", Casts.decimal(v.get("value_decimal"), 4));
			r.put("value_text", v.get("value_text"));
			r.put("value_json", v.get("value_json"));
			r.put("definition", definitions.get(Relations.key(v.get("production_product_attribute_definition_id"))));
			r.put("created_at", v.get("created_at"));
			r.put("updated_at", v.get("updated_at"));
			out.computeIfAbsent(v.longValue("material_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	/**
	 * Kural ve attribute degerlerindeki "definition": urun-attribute eslemesi; attribute_key, label,
	 * data_type, unit, description eslemenin attributeDefinition'indan (accessor).
	 */
	public Map<Long, Map<String, Object>> definitions(List<Long> ids) {
		List<Row> mappings = db.whereIn("production_product_attribute_definitions", "id", ids, null);
		Map<Long, Row> attributes = Relations.byId(db.whereIn("attribute_definitions", "id",
			Relations.column(mappings, "attribute_definition_id"), null));
		Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
		for (Row m : mappings) {
			Row d = attributes.get(Relations.key(m.get("attribute_definition_id")));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", m.get("id"));
			r.put("production_product_id", m.get("production_product_id"));
			r.put("attribute_key", d == null ? null : d.get("attribute_key"));
			r.put("label", d == null ? null : d.get("label"));
			r.put("data_type", d == null ? null : d.get("data_type"));
			r.put("unit", d == null ? null : d.get("unit"));
			r.put("is_required", Casts.bool(m.get("is_required")));
			r.put("sort_order", Casts.integer(m.get("sort_order")));
			r.put("description", d == null ? null : d.get("description"));
			out.put(m.id(), r);
		}
		return out;
	}

	static Map<String, Object> batchProcess(Row p) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", p.get("id"));
		r.put("production_batch_id", p.get("production_batch_id"));
		r.put("production_stage_id", p.get("production_stage_id"));
		r.put("status", p.get("status"));
		return r;
	}

	/** {id, key, name, description} (belongsTo yuklu; satir yoksa null). */
	private static Map<String, Object> product(Row p, boolean withDescription) {
		if (p == null) {
			return null;
		}
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", p.get("id"));
		r.put("key", p.get("key"));
		r.put("name", p.get("name"));
		if (withDescription) {
			r.put("description", p.get("description"));
		}
		return r;
	}

}
