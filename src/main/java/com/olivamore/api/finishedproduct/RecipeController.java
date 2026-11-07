package com.olivamore.api.finishedproduct;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code FinishedProductRecipeController} + {@code FinishedProductRecipeService}: dolum tarifleri.
 * Guncelleme yeni versiyon olusturur, eskisi pasife alinir ve urunler yeni versiyona tasinir.
 */
@RestController
class RecipeController {

	private final Db db;
	private final LaravelValidator validator;

	RecipeController(Db db, LaravelValidator validator) {
		this.db = db;
		this.validator = validator;
	}

	/** Aktif tarifler, en yeni once; {@code ?name=} (bos ya da "0" degilse) ilike. */
	@GetMapping("/api/finished-product-recipes")
	@RequiresPermission("finished_product_recipe.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		String name = request.getParameter("name");
		List<Row> rows = name != null && !name.isEmpty() && !"0".equals(name)
			? db.select("select * from finished_product_recipes where name::text ilike ? and is_active = true order by created_at desc",
				"%" + name + "%")
			: db.select("select * from finished_product_recipes where is_active = true order by created_at desc");
		return new ListResponse.Plain<>(resources(rows));
	}

	@GetMapping("/api/finished-product-recipes/{recipe}")
	@RequiresPermission("finished_product_recipe.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		return DataResponse.of(byId(recipe.id()));
	}

	/** Ayni addaki butun versiyonlar, yeniden eskiye. Laravel'de izin grubunun disinda: yalnizca giris. */
	@GetMapping("/api/finished-product-recipes/{recipe}/versions")
	ListResponse<Map<String, Object>> versions(@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		return new ListResponse.Plain<>(resources(db.select(
			"select * from finished_product_recipes where name = ? order by version desc", recipe.get("name"))));
	}

	/** 201: versiyon 1, aktif. */
	@PostMapping("/api/finished-product-recipes")
	@RequiresPermission("finished_product_recipe.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, rules("required", "required"),
			(data, add) -> ratioTotal(data.containsKey("contents") ? data.get("contents") : List.of(), add));
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("description", v.get("description"));
		values.put("version", 1);
		values.put("is_active", true);
		long id = db.insert("finished_product_recipes", values);
		syncContents(id, items(v.get("contents")));
		syncAttributeRules(id, items(v.get("attribute_rules")));
		return DataResponse.created(byId(id));
	}

	/**
	 * Yeni versiyon (201; Laravel'de yeni olusan model). Yalnizca aktif versiyon guncellenir; verilmeyen
	 * icerik ve kurallar eski versiyondan kopyalanir.
	 */
	@RequestMapping(path = "/api/finished-product-recipes/{recipe}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("finished_product_recipe.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> update(
		@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe, RequestInput input) {
		ValidatedInput v = validator.validate(input, rules("sometimes", "required_with:contents"), (data, add) -> {
			if (data.get("contents") != null) {
				ratioTotal(data.get("contents"), add);
			}
		});
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
		return DataResponse.created(byId(id));
	}

	@DeleteMapping("/api/finished-product-recipes/{recipe}")
	@RequiresPermission("finished_product_recipe.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		if (db.count("select count(*) from finished_product_runs where finished_product_recipe_id = ?", recipe.id()) > 0) {
			throw new BusinessRuleException("'" + recipe.string("name") + "' v" + recipe.get("version")
				+ " ile yapılmış dolum koşuları var; silinemez.");
		}
		if (db.count("select count(*) from finished_products where finished_product_recipe_id = ?", recipe.id()) > 0) {
			throw new BusinessRuleException("Bu tarif bir son ürüne bağlı olduğu için silinemez.");
		}
		db.delete("finished_product_recipes", recipe.id());
		return Map.of("message", "Tarif başarıyla silindi.");
	}

	// ---- dogrulama ---------------------------------------------------------------

	/**
	 * Laravel kurallari; {@code distinct} Java'da eklendi: ayni urun tipi ya da attribute iki kez gelince
	 * Laravel unique kisitina takilip 500 veriyordu (DEVIATIONS).
	 */
	private static Rules rules(String presence, String itemPresence) {
		return Rules.of()
			.field("name", presence + "|string|max:255")
			.field("description", "nullable|string")
			.field("contents", presence + "|array|min:1")
			.field("contents.*.production_product_type_id", itemPresence + "|integer|distinct|exists:production_product_types,id")
			.field("contents.*.ratio", itemPresence + "|numeric|min:0.0001|max:1")
			.field("attribute_rules", "nullable|array")
			.field("attribute_rules.*.attribute_definition_id", "required|integer|distinct|exists:attribute_definitions,id")
			.field("attribute_rules.*.value", "required|numeric|min:0")
			.field("attribute_rules.*.min_deviation", "nullable|numeric|min:0")
			.field("attribute_rules.*.max_deviation", "nullable|numeric|min:0");
	}

	/**
	 * {@code collect($contents)->sum('ratio')}, 4 basamaga yuvarlanmis toplam 1 olmali. Laravel'de sayi olmayan
	 * oran (metin, dizi) toplamada 500 veriyor; burada 0 sayilir, alanin kendi hatasi zaten {@code numeric}.
	 */
	static void ratioTotal(Object contents, java.util.function.BiConsumer<String, String> add) {
		Collection<?> list = contents instanceof Map<?, ?> m ? m.values() : contents instanceof List<?> l ? l : List.of(contents);
		double total = 0;
		for (Object item : list) {
			Object ratio = item instanceof Map<?, ?> m ? m.get("ratio") : null;
			total += switch (ratio) {
				case null -> 0;
				case Number n -> n.doubleValue();
				case Boolean b -> b ? 1 : 0;
				case String s when LaravelValidator.numeric(s) -> Php.floatval(s.strip());
				default -> 0;
			};
		}
		double rounded = Php.round(total, 4);
		if (rounded != 1.0) {
			add.accept("contents", "İçerik oranlarının toplamı 1.00 olmalıdır. Mevcut toplam: " + Php.floatString(rounded));
		}
	}

	// ---- yazma yardimcilari ------------------------------------------------------

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

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> items(Object value) {
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>((Collection<Map<String, Object>>) m.values());
		}
		return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

	private static Map<String, Object> row(String k1, Object v1, String k2, Object v2) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put(k1, v1);
		m.put(k2, v2);
		return m;
	}

	// ---- resource ----------------------------------------------------------------

	private Map<String, Object> byId(long id) {
		return resources(List.of(db.find("finished_product_recipes", id).orElseThrow())).getFirst();
	}

	/** FinishedProductRecipeResource; icerik (urun tipiyle) ve attribute kurallari (tanimiyla) yuklu. */
	private List<Map<String, Object>> resources(List<Row> recipes) {
		List<Long> ids = Relations.ids(recipes);
		List<Row> contentRows = db.whereIn("finished_product_recipe_contents", "finished_product_recipe_id", ids, null);
		List<Row> ruleRows = db.whereIn("finished_product_recipe_attribute_rules", "finished_product_recipe_id", ids, null);
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
			r.put("created_at", recipe.get("created_at"));
			r.put("updated_at", recipe.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** Tarif icerigi ({@code ratio}: decimal:4); urun detayi da kullanir. */
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
