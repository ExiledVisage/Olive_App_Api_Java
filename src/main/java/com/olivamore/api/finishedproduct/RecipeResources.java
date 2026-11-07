package com.olivamore.api.finishedproduct;

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
 * Laravel {@code FinishedProductRecipeResource}. Musteri modulu de kullanir (musterinin formul profili:
 * icerik yuklu, attribute kurallari yuklu degil).
 */
@Component
public class RecipeResources {

	private final Db db;

	RecipeResources(Db db) {
		this.db = db;
	}

	/**
	 * @param withRules attribute kurallari yuklu mu ({@code attributeRules.attributeDefinition}); degilse
	 *                  {@code attribute_rules} anahtari yok
	 */
	public List<Map<String, Object>> recipes(List<Row> recipes, boolean withRules) {
		List<Long> ids = Relations.ids(recipes);
		List<Row> contentRows = db.whereIn("finished_product_recipe_contents", "finished_product_recipe_id", ids, null);
		List<Row> ruleRows = withRules ? db.whereIn("finished_product_recipe_attribute_rules", "finished_product_recipe_id", ids, null) : List.of();
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(contentRows, "production_product_type_id"), null));
		Map<Long, Row> definitions = Relations.byId(db.whereIn("attribute_definitions", "id",
			Relations.column(ruleRows, "attribute_definition_id"), null));
		Map<Long, List<Row>> contents = Relations.groupBy(contentRows, "finished_product_recipe_id");
		Map<Long, List<Row>> rules = Relations.groupBy(ruleRows, "finished_product_recipe_id");
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row recipe : recipes) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", recipe.get("id"));
			r.put("name", recipe.get("name"));
			r.put("version", recipe.get("version"));
			r.put("is_active", Casts.bool(recipe.get("is_active")));
			r.put("description", recipe.get("description"));
			r.put("contents", contents(contents.getOrDefault(recipe.id(), List.of()), types));
			if (withRules) {
				List<Map<String, Object>> ruleList = new ArrayList<>();
				for (Row rule : rules.getOrDefault(recipe.id(), List.of())) {
					Row definition = definitions.get(rule.longValue("attribute_definition_id"));
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("id", rule.get("id"));
					m.put("attribute_definition_id", rule.get("attribute_definition_id"));
					m.put("attribute_label", definition == null ? null : definition.get("label"));
					m.put("value", Casts.decimal(rule.get("value"), 4));
					m.put("min_deviation", Casts.decimal(rule.get("min_deviation"), 4));
					m.put("max_deviation", Casts.decimal(rule.get("max_deviation"), 4));
					ruleList.add(m);
				}
				r.put("attribute_rules", ruleList);
			}
			r.put("created_at", recipe.get("created_at"));
			r.put("updated_at", recipe.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** Tarif icerigi ({@code ratio}: decimal:4). */
	static List<Map<String, Object>> contents(List<Row> rows, Map<Long, Row> types) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row c : rows) {
			Row type = types.get(c.longValue("production_product_type_id"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", c.get("id"));
			m.put("production_product_type_id", c.get("production_product_type_id"));
			m.put("production_product_type_name", type == null ? null : type.get("name"));
			m.put("ratio", Casts.decimal(c.get("ratio"), 4));
			out.add(m);
		}
		return out;
	}

}
