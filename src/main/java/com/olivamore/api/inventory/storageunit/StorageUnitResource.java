package com.olivamore.api.inventory.storageunit;

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
import com.olivamore.api.inventory.material.MaterialResource;

/**
 * Laravel {@code StorageUnitResource} ({@code StorageUnitService::relations()} + kapasite metrikleri) ve
 * {@code StorageUnitBalanceResource}.
 */
@Component
class StorageUnitResource {

	private final Db db;
	private final MaterialResource materialResource;

	StorageUnitResource(Db db, MaterialResource materials) {
		this.db = db;
		this.materialResource = materials;
	}

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
			List<Map<String, Object>> resources = materialResource.materials(rows, MaterialResource.MaterialLoad.TYPE_AND_ATTRIBUTES);
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
		Map<Long, Map<String, Object>> definitions = materialResource.definitions(Relations.column(rows, "production_product_attribute_definition_id"));
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
