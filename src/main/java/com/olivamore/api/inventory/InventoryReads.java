package com.olivamore.api.inventory;

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
 * Tank, bakiye, materyal ve hareket iliskilerinin yuklenmesi (Eloquent eager loading) ve JsonResource'lar.
 * Iliskiler Laravel'in sorgulariyla ayni bicimde yuklenir (siralanmayan iliskilerde siralama yok);
 * {@code whenLoaded} ile yuklenmemis iliskinin anahtari hic yazilmaz.
 */
@Component
class InventoryReads {

	private final Db db;

	InventoryReads(Db db) {
		this.db = db;
	}

	// ======================================================================
	// Tanklar (StorageUnitResource)
	// ======================================================================

	/** StorageUnitService::relations() + appendCapacityMetrics. */
	List<Map<String, Object>> storageUnits(List<Row> units) {
		List<Long> ids = Relations.ids(units);
		Map<Long, List<Map<String, Object>>> allowedProducts = allowedProducts(ids);
		Map<Long, List<Map<String, Object>>> allowedTypes = allowedTypes(ids);
		Map<Long, List<Map<String, Object>>> rules = attributeRules(ids);
		Map<Long, List<Row>> balanceRows = Relations.groupBy(
			db.whereIn("storage_unit_balances", "storage_unit_id", ids, null), "storage_unit_id");
		Map<Long, Row> balanceProducts = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(balanceRows.values().stream().flatMap(List::stream).toList(), "production_product_id"), null));
		Map<Long, List<Map<String, Object>>> materials = new LinkedHashMap<>();
		if (!ids.isEmpty()) {
			List<Row> rows = db.select("select * from materials where materials.current_location_id in ("
				+ Relations.inList(ids) + ") and current_location_type = ? and status = ?", "storage_unit", "active");
			List<Map<String, Object>> resources = materials(rows, MaterialLoad.TYPE_AND_ATTRIBUTES);
			for (int i = 0; i < rows.size(); i++) {
				materials.computeIfAbsent(rows.get(i).longValue("current_location_id"), k -> new ArrayList<>()).add(resources.get(i));
			}
		}

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row u : units) {
			List<Row> balances = balanceRows.getOrDefault(u.id(), List.of());
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", u.get("id"));
			r.put("name", u.get("name"));
			r.put("code", u.get("code"));
			r.put("capacity", Casts.decimal(u.get("capacity"), 2));
			r.put("capacity_unit", u.get("capacity_unit"));
			r.put("is_blend_tank", Casts.bool(u.get("is_blend_tank")));
			r.put("allow_multiple_production_product_types", Casts.bool(u.get("allow_multiple_production_product_types")));
			r.put("allow_multiple_production_products", Casts.bool(u.get("allow_multiple_production_products")));
			r.put("description", u.get("description"));
			r.put("is_active", Casts.bool(u.get("is_active")));
			capacityMetrics(u, balances, r);
			r.put("allowed_production_products", allowedProducts.getOrDefault(u.id(), List.of()));
			r.put("allowed_production_product_types", allowedTypes.getOrDefault(u.id(), List.of()));
			r.put("attribute_rules", rules.getOrDefault(u.id(), List.of()));
			r.put("balances", balances.stream().map(b -> balance(b, balanceProducts, true)).toList());
			r.put("current_materials", materials.getOrDefault(u.id(), List.of()));
			r.put("created_at", u.get("created_at"));
			r.put("updated_at", u.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	Map<String, Object> storageUnit(Row unit) {
		return storageUnits(List.of(unit)).getFirst();
	}

	/**
	 * appendCapacityMetrics: doluluk yalnizca tankin kapasite birimindeki bakiyelerden; PHP float
	 * aritmetigi ve {@code round(..., 2)}.
	 */
	private static void capacityMetrics(Row unit, List<Row> balances, Map<String, Object> r) {
		double filled = 0;
		for (Row b : balances) {
			if (Php.strval(b.get("unit")).equals(Php.strval(unit.get("capacity_unit")))) {
				filled += Php.floatval(Casts.decimal(b.get("current_quantity"), 2));
			}
		}
		double capacity = Php.floatval(Casts.decimal(unit.get("capacity"), 2));
		double remaining = Math.max(0, capacity - filled);
		double filledPercentage = capacity > 0 ? Php.round((filled / capacity) * 100, 2) : 0.0;
		double emptyPercentage = Php.round(Math.max(0, 100 - filledPercentage), 2);
		r.put("filled_quantity", Php.jsonFloat(Php.round(filled, 2)));
		r.put("remaining_capacity", Php.jsonFloat(Php.round(remaining, 2)));
		r.put("filled_percentage", Php.jsonFloat(filledPercentage));
		r.put("empty_percentage", Php.jsonFloat(emptyPercentage));
	}

	/** StorageUnitAllowedProductionProductResource (productionProduct yuklu). */
	private Map<Long, List<Map<String, Object>>> allowedProducts(List<Long> unitIds) {
		List<Row> rows = db.whereIn("storage_unit_allowed_production_products", "storage_unit_id", unitIds, null);
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(rows, "production_product_id"), null));
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row a : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", a.get("id"));
			r.put("storage_unit_id", a.get("storage_unit_id"));
			r.put("production_product_id", a.get("production_product_id"));
			r.put("production_product", product(products.get(Relations.key(a.get("production_product_id"))), true));
			r.put("created_at", a.get("created_at"));
			r.put("updated_at", a.get("updated_at"));
			out.computeIfAbsent(a.longValue("storage_unit_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	/** StorageUnitAllowedProductionProductTypeResource (tipin kategorisi yuklenmiyor: production_product null). */
	private Map<Long, List<Map<String, Object>>> allowedTypes(List<Long> unitIds) {
		List<Row> rows = db.whereIn("storage_unit_allowed_production_product_types", "storage_unit_id", unitIds, null);
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(rows, "production_product_type_id"), null));
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row a : rows) {
			Row t = types.get(Relations.key(a.get("production_product_type_id")));
			Map<String, Object> type = null;
			if (t != null) {
				type = new LinkedHashMap<>();
				type.put("id", t.get("id"));
				type.put("production_product_id", t.get("production_product_id"));
				type.put("name", t.get("name"));
				type.put("code", t.get("code"));
				type.put("type", t.get("type"));
				type.put("unit", t.get("unit"));
				type.put("production_product", null);
			}
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", a.get("id"));
			r.put("storage_unit_id", a.get("storage_unit_id"));
			r.put("production_product_type_id", a.get("production_product_type_id"));
			r.put("production_product_type", type);
			r.put("created_at", a.get("created_at"));
			r.put("updated_at", a.get("updated_at"));
			out.computeIfAbsent(a.longValue("storage_unit_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	/** StorageUnitAttributeRuleResource (definition yuklu; min/max decimal:4). */
	private Map<Long, List<Map<String, Object>>> attributeRules(List<Long> unitIds) {
		List<Row> rows = db.whereIn("storage_unit_attribute_rules", "storage_unit_id", unitIds, null);
		Map<Long, Map<String, Object>> definitions = definitions(Relations.column(rows, "production_product_attribute_definition_id"));
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row a : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", a.get("id"));
			r.put("storage_unit_id", a.get("storage_unit_id"));
			r.put("production_product_attribute_definition_id", a.get("production_product_attribute_definition_id"));
			r.put("min_value_decimal", Casts.decimal(a.get("min_value_decimal"), 4));
			r.put("max_value_decimal", Casts.decimal(a.get("max_value_decimal"), 4));
			r.put("apply_mode", a.get("apply_mode"));
			r.put("is_active", Casts.bool(a.get("is_active")));
			r.put("note", a.get("note"));
			r.put("definition", definitions.get(Relations.key(a.get("production_product_attribute_definition_id"))));
			r.put("created_at", a.get("created_at"));
			r.put("updated_at", a.get("updated_at"));
			out.computeIfAbsent(a.longValue("storage_unit_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	// ======================================================================
	// Bakiyeler (StorageUnitBalanceResource)
	// ======================================================================

	/** {@code balances()->with('productionProduct')->get()} */
	List<Map<String, Object>> balances(List<Row> balances) {
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(balances, "production_product_id"), null));
		return balances.stream().map(b -> balance(b, products, true)).toList();
	}

	private static Map<String, Object> balance(Row b, Map<Long, Row> products, boolean withProduct) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", b.get("id"));
		r.put("storage_unit_id", b.get("storage_unit_id"));
		r.put("production_product_id", b.get("production_product_id"));
		r.put("current_quantity", Casts.decimal(b.get("current_quantity"), 2));
		r.put("unit", b.get("unit"));
		if (withProduct) {
			r.put("production_product", product(products.get(Relations.key(b.get("production_product_id"))), true));
		}
		r.put("created_at", b.get("created_at"));
		r.put("updated_at", b.get("updated_at"));
		return r;
	}

	// ======================================================================
	// Materyaller (MaterialResource, MaterialAttributeValueResource)
	// ======================================================================

	/** MaterialResource'ta hangi iliskiler yuklu. */
	record MaterialLoad(boolean type, boolean attributeValues) {

		/** productionProductType.productionProduct + attributeValues.definition */
		static final MaterialLoad TYPE_AND_ATTRIBUTES = new MaterialLoad(true, true);

	}

	List<Map<String, Object>> materials(List<Row> materials, MaterialLoad load) {
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
			if (load.attributeValues()) {
				r.put("attribute_values", values.getOrDefault(m.id(), List.of()));
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
	static Number effectiveDensity(Row type, Row product) {
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
	private Map<Long, Map<String, Object>> definitions(List<Long> ids) {
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

	// ======================================================================
	// Hareketler (StorageMovementResource)
	// ======================================================================

	/**
	 * StorageUnitService::getMovements iliskileri: material (tip + attribute degerleri), batchProcess,
	 * counterpartyStorageUnit. storageUnit yuklu degil (anahtar yok).
	 */
	List<Map<String, Object>> movements(List<Row> movements) {
		List<Row> materialRows = db.whereIn("materials", "id", Relations.column(movements, "material_id"), null);
		List<Map<String, Object>> materialResources = materials(materialRows, MaterialLoad.TYPE_AND_ATTRIBUTES);
		Map<Long, Map<String, Object>> materials = new LinkedHashMap<>();
		for (int i = 0; i < materialRows.size(); i++) {
			materials.put(materialRows.get(i).id(), materialResources.get(i));
		}
		Map<Long, Row> processes = Relations.byId(db.whereIn("batch_processes", "id", Relations.column(movements, "batch_process_id"), null));
		Map<Long, Row> counterparties = Relations.byId(db.whereIn("storage_units", "id",
			Relations.column(movements, "counterparty_storage_unit_id"), null));

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row m : movements) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", m.get("id"));
			r.put("storage_unit_id", m.get("storage_unit_id"));
			r.put("material_id", m.get("material_id"));
			r.put("batch_process_id", m.get("batch_process_id"));
			r.put("finished_product_run_id", m.get("finished_product_run_id"));
			r.put("counterparty_storage_unit_id", m.get("counterparty_storage_unit_id"));
			r.put("source_type", m.get("source_type"));
			r.put("movement_type", m.get("movement_type"));
			r.put("transfer_reference", m.get("transfer_reference"));
			r.put("quantity", Casts.decimal(m.get("quantity"), 2));
			r.put("unit", m.get("unit"));
			r.put("note", m.get("note"));
			r.put("moved_at", m.get("moved_at"));
			r.put("material", materials.get(Relations.key(m.get("material_id"))));
			Row p = processes.get(Relations.key(m.get("batch_process_id")));
			r.put("batch_process", p == null ? null : batchProcess(p));
			Row c = counterparties.get(Relations.key(m.get("counterparty_storage_unit_id")));
			r.put("counterparty_storage_unit", c == null ? null : unitSummary(c));
			r.put("created_at", m.get("created_at"));
			r.put("updated_at", m.get("updated_at"));
			out.add(r);
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

	static Map<String, Object> unitSummary(Row u) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", u.get("id"));
		r.put("name", u.get("name"));
		r.put("code", u.get("code"));
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
