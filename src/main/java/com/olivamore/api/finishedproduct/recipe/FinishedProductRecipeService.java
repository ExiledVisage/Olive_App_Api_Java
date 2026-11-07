package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.ValidatedInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code FinishedProductRecipeService}: dolum tarifleri. Guncelleme yeni versiyon olusturur, eskisi pasife
 * alinir ve urunler yeni versiyona tasinir.
 */
@Service
class FinishedProductRecipeService {

	private final Db db;

	FinishedProductRecipeService(Db db) {
		this.db = db;
	}

	/** Aktif tarifler, en yeni once; {@code name} (bos ya da "0" degilse) ilike. */
	List<Row> active(String name) {
		return name != null && !name.isEmpty() && !"0".equals(name)
			? db.select("select * from finished_product_recipes where name::text ilike ? and is_active = true order by created_at desc",
				"%" + name + "%")
			: db.select("select * from finished_product_recipes where is_active = true order by created_at desc");
	}

	/** Ayni addaki butun versiyonlar, yeniden eskiye. */
	List<Row> versions(Row recipe) {
		return db.select("select * from finished_product_recipes where name = ? order by version desc", recipe.get("name"));
	}

	Row byId(long id) {
		return db.find("finished_product_recipes", id).orElseThrow();
	}

	/** Versiyon 1, aktif. @return yeni tarif id */
	long create(ValidatedInput v) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("description", v.get("description"));
		values.put("version", 1);
		values.put("is_active", true);
		long id = db.insert("finished_product_recipes", values);
		syncContents(id, items(v.get("contents")));
		syncAttributeRules(id, items(v.get("attribute_rules")));
		return id;
	}

	/** Yeni versiyon. Yalnizca aktif versiyon guncellenir; verilmeyen icerik ve kurallar eski versiyondan kopyalanir. @return yeni versiyonun id'si */
	long update(Row recipe, ValidatedInput v) {
		if (!Boolean.TRUE.equals(Casts.bool(recipe.get("is_active")))) {
			Row latest = db.first("select * from finished_product_recipes where name = ? and is_active = true limit 1",
				recipe.get("name")).orElse(null);
			throw new BusinessRuleException("Sadece aktif tarif versiyonu güncellenebilir. '" + recipe.string("name") + "' v"
				+ recipe.get("version") + " pasif"
				+ (latest != null ? "; güncel versiyon v" + latest.get("version") + " (id " + latest.id() + ")." : "."));
		}
		db.update("finished_product_recipes", recipe.id(), Map.of("is_active", false));
		Object name = v.has("name") && v.get("name") != null ? v.get("name") : recipe.get("name");
		long next = db.count("select coalesce(max(version), 0) from finished_product_recipes where name = ?", name) + 1;
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", name);
		values.put("description", v.all().containsKey("description") ? v.get("description") : recipe.get("description"));
		values.put("version", Math.max(next, Php.intval(recipe.get("version")) + 1));
		values.put("is_active", true);
		long id = db.insert("finished_product_recipes", values);

		List<Map<String, Object>> contents = v.get("contents") != null ? items(v.get("contents"))
			: db.select("select * from finished_product_recipe_contents where finished_product_recipe_id = ?", recipe.id()).stream()
				.map(c -> row("production_product_type_id", c.get("production_product_type_id"), "ratio", Casts.decimal(c.get("ratio"), 4)))
				.toList();
		List<Map<String, Object>> rules = v.get("attribute_rules") != null ? items(v.get("attribute_rules"))
			: db.select("select * from finished_product_recipe_attribute_rules where finished_product_recipe_id = ?", recipe.id()).stream()
				.map(r -> {
					Map<String, Object> m = row("attribute_definition_id", r.get("attribute_definition_id"), "value", Casts.decimal(r.get("value"), 4));
					m.put("min_deviation", Casts.decimal(r.get("min_deviation"), 4));
					m.put("max_deviation", Casts.decimal(r.get("max_deviation"), 4));
					return m;
				})
				.toList();
		syncContents(id, contents);
		syncAttributeRules(id, rules);
		db.execute("update finished_products set finished_product_recipe_id = ?, updated_at = ? where finished_product_recipe_id = ?",
			id, Timestamps.now(), recipe.id());
		return id;
	}

	void delete(Row recipe) {
		if (db.count("select count(*) from finished_product_runs where finished_product_recipe_id = ?", recipe.id()) > 0) {
			throw new BusinessRuleException("'" + recipe.string("name") + "' v" + recipe.get("version")
				+ " ile yapılmış dolum koşuları var; silinemez.");
		}
		if (db.count("select count(*) from finished_products where finished_product_recipe_id = ?", recipe.id()) > 0) {
			throw new BusinessRuleException("Bu tarif bir son ürüne bağlı olduğu için silinemez.");
		}
		db.delete("finished_product_recipes", recipe.id());
	}

	// ---- yardimcilar ------------------------------------------------------

	private void syncContents(long recipeId, List<Map<String, Object>> contents) {
		for (Map<String, Object> c : contents) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("production_product_type_id", c.get("production_product_type_id"));
			values.put("ratio", c.get("ratio"));
			values.put("finished_product_recipe_id", recipeId);
			db.insert("finished_product_recipe_contents", values);
		}
	}

	private void syncAttributeRules(long recipeId, List<Map<String, Object>> rules) {
		for (Map<String, Object> r : rules) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("attribute_definition_id", r.get("attribute_definition_id"));
			values.put("value", r.get("value"));
			values.put("min_deviation", r.get("min_deviation"));
			values.put("max_deviation", r.get("max_deviation"));
			values.put("finished_product_recipe_id", recipeId);
			db.insert("finished_product_recipe_attribute_rules", values);
		}
	}

	private static Map<String, Object> row(String k1, Object v1, String k2, Object v2) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put(k1, v1);
		m.put(k2, v2);
		return m;
	}

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> items(Object value) {
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>((Collection<Map<String, Object>>) m.values());
		}
		return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

}
