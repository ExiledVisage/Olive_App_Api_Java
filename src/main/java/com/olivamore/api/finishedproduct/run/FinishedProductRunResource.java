package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.finishedproduct.RecipeResources;
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

/** Laravel {@code FinishedProductRunResource}: hangi iliskiler yukluyse o alanlar. */
@Component
class FinishedProductRunResource {

	/** {@code FinishedProductRunService::TRANSITIONS} */
	static final Map<String, List<String>> TRANSITIONS = Map.of(
		"planned", List.of("in_progress", "cancelled"),
		"in_progress", List.of("completed", "cancelled"));

	/** Liste: urun, tarif, tank. */
	static final Shape LIST = new Shape(false, true, false);
	/** Detay ({@code getById}): her sey, tarifin icerigi dahil. */
	static final Shape DETAIL = new Shape(true, true, true);
	/** Ekleme ve durum cevabi: tank ve tarif icerigi yok. */
	static final Shape WRITTEN = new Shape(false, false, true);

	/**
	 * @param recipeContents tarifin icerigi yuklu mu
	 * @param sourceUnit     tank yuklu mu
	 * @param details        materyaller, ambalaj tahsisi ve attribute degerleri yuklu mu
	 */
	record Shape(boolean recipeContents, boolean sourceUnit, boolean details) {
	}

	private final Db db;

	FinishedProductRunResource(Db db) {
		this.db = db;
	}

	Map<String, Object> one(Row run, Shape set) {
		return runs(List.of(run), set).getFirst();
	}

	List<Map<String, Object>> runs(List<Row> runs, Shape set) {
		List<Long> ids = Relations.ids(runs);
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(runs, "finished_product_id"), null));
		Map<Long, Row> recipes = Relations.byId(db.whereIn("finished_product_recipes", "id",
			Relations.column(runs, "finished_product_recipe_id"), null));
		Map<Long, List<Row>> contents = Map.of();
		Map<Long, Row> contentTypes = Map.of();
		if (set.recipeContents()) {
			List<Row> rows = db.whereIn("finished_product_recipe_contents", "finished_product_recipe_id", List.copyOf(recipes.keySet()), null);
			contentTypes = Relations.byId(db.whereIn("production_product_types", "id", Relations.column(rows, "production_product_type_id"), null));
			contents = Relations.groupBy(rows, "finished_product_recipe_id");
		}
		Map<Long, Row> units = set.sourceUnit()
			? Relations.byId(db.whereIn("storage_units", "id", Relations.column(runs, "source_storage_unit_id"), null))
			: Map.of();

		Map<Long, List<Row>> materials = Map.of();
		Map<Long, Row> sourceMaterials = Map.of();
		Map<Long, Row> types = Map.of();
		Map<Long, Row> productionProducts = Map.of();
		Map<Long, List<Row>> packagings = Map.of();
		Map<Long, Row> items = Map.of();
		Map<Long, Row> lots = Map.of();
		Map<Long, Row> suppliers = Map.of();
		Map<Long, List<Row>> values = Map.of();
		Map<Long, Row> definitions = Map.of();
		if (set.details() && !ids.isEmpty()) {
			List<Row> materialRows = db.whereIn("finished_product_run_materials", "finished_product_run_id", ids, null);
			sourceMaterials = Relations.byId(db.whereIn("materials", "id", Relations.column(materialRows, "source_material_id"), null));
			types = Relations.byId(db.whereIn("production_product_types", "id",
				Relations.column(List.copyOf(sourceMaterials.values()), "production_product_type_id"), null));
			productionProducts = Relations.byId(db.whereIn("production_products", "id",
				Relations.column(List.copyOf(types.values()), "production_product_id"), null));
			materials = Relations.groupBy(materialRows, "finished_product_run_id");

			List<Row> packagingRows = db.whereIn("finished_product_run_packagings", "finished_product_run_id", ids, null);
			items = Relations.byId(db.whereIn("packaging_items", "id", Relations.column(packagingRows, "packaging_item_id"), null));
			lots = Relations.byId(db.whereIn("packaging_lots", "id", Relations.column(packagingRows, "packaging_lot_id"), null));
			suppliers = Relations.byId(db.whereIn("product_suppliers", "id",
				Relations.column(List.copyOf(lots.values()), "product_supplier_id"), null));
			packagings = Relations.groupBy(packagingRows, "finished_product_run_id");

			List<Row> valueRows = db.whereIn("finished_product_run_attribute_values", "finished_product_run_id", ids, null);
			definitions = Relations.byId(db.whereIn("attribute_definitions", "id", Relations.column(valueRows, "attribute_definition_id"), null));
			values = Relations.groupBy(valueRows, "finished_product_run_id");
		}

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row run : runs) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", run.get("id"));
			r.put("quantity", run.get("quantity"));
			r.put("status", run.get("status"));
			r.put("allowed_transitions", TRANSITIONS.getOrDefault(run.string("status"), List.of()));
			r.put("note", run.get("note"));
			r.put("started_at", run.get("started_at"));
			r.put("completed_at", run.get("completed_at"));

			Row product = products.get(run.longValue("finished_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product.get("id"));
			p.put("name", product.get("name"));
			p.put("volume_litre", Casts.decimal(product.get("volume_litre"), 4));
			r.put("finished_product", p);

			Row recipe = recipes.get(run.longValue("finished_product_recipe_id"));
			Map<String, Object> rec = new LinkedHashMap<>();
			rec.put("id", recipe.get("id"));
			rec.put("name", recipe.get("name"));
			rec.put("version", recipe.get("version"));
			if (set.recipeContents()) {
				rec.put("contents", RecipeResources.contents(contents.getOrDefault(recipe.id(), List.of()), contentTypes));
			}
			r.put("recipe", rec);

			if (set.sourceUnit()) {
				Row unit = run.longValue("source_storage_unit_id") == null ? null : units.get(run.longValue("source_storage_unit_id"));
				if (unit == null) {
					r.put("source_storage_unit", null);
				}
				else {
					Map<String, Object> u = new LinkedHashMap<>();
					u.put("id", unit.get("id"));
					u.put("name", unit.get("name"));
					u.put("code", unit.get("code"));
					r.put("source_storage_unit", u);
				}
			}

			if (set.details()) {
				List<Map<String, Object>> ms = new ArrayList<>();
				for (Row m : materials.getOrDefault(run.id(), List.of())) {
					Row material = sourceMaterials.get(m.longValue("source_material_id"));
					Row type = material == null || material.longValue("production_product_type_id") == null ? null
						: types.get(material.longValue("production_product_type_id"));
					Map<String, Object> x = new LinkedHashMap<>();
					x.put("id", m.get("id"));
					x.put("source_material_id", m.get("source_material_id"));
					x.put("quantity_used", Casts.decimal(m.get("quantity_used"), 4));
					x.put("unit", material == null ? null : material.get("unit"));
					x.put("production_product_type_id", material == null ? null : material.get("production_product_type_id"));
					x.put("production_product_type_name", type == null ? null : type.get("name"));
					x.put("density_kg_per_litre", type == null ? null
						: effectiveDensity(type, type.longValue("production_product_id") == null ? null
							: productionProducts.get(type.longValue("production_product_id"))));
					ms.add(x);
				}
				r.put("materials", ms);

				List<Map<String, Object>> ps = new ArrayList<>();
				for (Row pk : packagings.getOrDefault(run.id(), List.of())) {
					Row item = items.get(pk.longValue("packaging_item_id"));
					Row lot = lots.get(pk.longValue("packaging_lot_id"));
					Row supplier = lot == null || lot.longValue("product_supplier_id") == null ? null
						: suppliers.get(lot.longValue("product_supplier_id"));
					Map<String, Object> x = new LinkedHashMap<>();
					x.put("id", pk.get("id"));
					x.put("packaging_item_id", pk.get("packaging_item_id"));
					x.put("packaging_item_name", item == null ? null : item.get("name"));
					x.put("packaging_item_type", item == null ? null : item.get("type"));
					x.put("packaging_lot_id", pk.get("packaging_lot_id"));
					x.put("lot_code", lot == null ? null : lot.get("lot_code"));
					x.put("lot_received_at", lot == null ? null : lot.get("received_at"));
					x.put("lot_remaining", lot == null ? null : Php.intval(lot.get("quantity_remaining")));
					x.put("supplier_name", supplier == null ? null : supplier.get("name"));
					x.put("quantity", Php.intval(pk.get("quantity")));
					ps.add(x);
				}
				r.put("packagings", ps);

				List<Map<String, Object>> vs = new ArrayList<>();
				for (Row v : values.getOrDefault(run.id(), List.of())) {
					Row definition = definitions.get(v.longValue("attribute_definition_id"));
					Map<String, Object> x = new LinkedHashMap<>();
					x.put("id", v.get("id"));
					x.put("attribute_definition_id", v.get("attribute_definition_id"));
					x.put("attribute_label", definition == null ? null : definition.get("label"));
					x.put("estimated_value", Casts.decimal(v.get("estimated_value"), 4));
					x.put("actual_value", Casts.decimal(v.get("actual_value"), 4));
					vs.add(x);
				}
				r.put("attribute_values", vs);
			}
			r.put("created_at", run.get("created_at"));
			r.put("updated_at", run.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** {@code ProductionProductType::effectiveDensity()}: kendi > 0 ise o, yoksa kategorinin varsayilani > 0 ise o. */
	static Double effectiveDensity(Row type, Row product) {
		BigDecimal own = Casts.toDecimal(type.get("density_kg_per_litre"));
		if (own != null && own.signum() > 0) {
			return own.doubleValue();
		}
		BigDecimal fallback = product == null ? null : Casts.toDecimal(product.get("default_density_kg_per_litre"));
		return fallback != null && fallback.signum() > 0 ? fallback.doubleValue() : null;
	}

}
