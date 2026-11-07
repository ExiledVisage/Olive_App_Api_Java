package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code FinishedProductResource}: tarif, ambalaj bilesenleri, raf konumlari, rafta/sevkiyat bekleyen/acikta adet. */
@Component
class FinishedProductResource {

	private final Db db;

	FinishedProductResource(Db db) {
		this.db = db;
	}

	/**
	 * FinishedProductResource.
	 * @param recipeContents tarifin icerigi yuklu mu (detay)
	 * @param awaiting       sevkiyat bekleyen toplam yuklu mu
	 * @param locations      raf konumlari yuklu mu
	 */
	List<Map<String, Object>> of(List<Row> products, boolean recipeContents, boolean awaiting, boolean locations) {
		List<Long> ids = Relations.ids(products);
		Map<Long, Row> recipes = Relations.byId(db.whereIn("finished_product_recipes", "id",
			Relations.column(products, "finished_product_recipe_id"), null));
		Map<Long, List<Row>> contents = Map.of();
		Map<Long, Row> types = Map.of();
		if (recipeContents) {
			List<Row> contentRows = db.whereIn("finished_product_recipe_contents", "finished_product_recipe_id", List.copyOf(recipes.keySet()), null);
			types = Relations.byId(db.whereIn("production_product_types", "id", Relations.column(contentRows, "production_product_type_id"), null));
			contents = Relations.groupBy(contentRows, "finished_product_recipe_id");
		}
		List<Row> componentRows = db.whereIn("finished_product_components", "finished_product_id", ids, null);
		Map<Long, Row> items = Relations.byId(db.whereIn("packaging_items", "id", Relations.column(componentRows, "packaging_item_id"), null));
		Map<Long, List<Row>> components = Relations.groupBy(componentRows, "finished_product_id");
		Map<Long, List<Row>> slotStocks = Map.of();
		Map<Long, Row> slots = Map.of();
		Map<Long, Row> storages = Map.of();
		if (locations && !ids.isEmpty()) {
			List<Row> stockRows = db.select("select * from finished_product_storage_slot_stocks where finished_product_id in ("
				+ Relations.inList(ids) + ") and quantity > 0");
			slots = Relations.byId(db.whereIn("finished_product_storage_slots", "id",
				Relations.column(stockRows, "finished_product_storage_slot_id"), null));
			storages = Relations.byId(db.whereIn("finished_product_storages", "id",
				Relations.column(List.copyOf(slots.values()), "finished_product_storage_id"), null));
			slotStocks = Relations.groupBy(stockRows, "finished_product_id");
		}

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row p : products) {
			long total = Php.intval(p.get("total_stock"));
			long stored = Php.intval(p.get("stored_stock"));
			Object awaitingSum = awaiting ? p.get("awaiting_shipment_stock") : null;
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", p.get("id"));
			r.put("name", p.get("name"));
			r.put("volume_litre", Casts.decimal(p.get("volume_litre"), 4));
			r.put("total_stock", p.get("total_stock"));
			r.put("stored_stock", stored);
			r.put("awaiting_shipment_stock", awaitingSum == null ? null : Php.intval(awaitingSum));
			r.put("unstored_stock", Math.max(0, total - stored - (awaitingSum == null ? 0 : Php.intval(awaitingSum))));
			r.put("is_active", Casts.bool(p.get("is_active")));
			Row recipe = recipes.get(p.longValue("finished_product_recipe_id"));
			Map<String, Object> rec = new LinkedHashMap<>();
			rec.put("id", recipe.get("id"));
			rec.put("name", recipe.get("name"));
			rec.put("version", recipe.get("version"));
			if (recipeContents) {
				rec.put("contents", RecipeResources.contents(contents.getOrDefault(recipe.id(), List.of()), types));
			}
			r.put("recipe", rec);
			List<Map<String, Object>> comps = new ArrayList<>();
			for (Row c : components.getOrDefault(p.id(), List.of())) {
				Row item = items.get(c.longValue("packaging_item_id"));
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", c.get("id"));
				m.put("packaging_item_id", c.get("packaging_item_id"));
				m.put("packaging_item_name", item == null ? null : item.get("name"));
				m.put("packaging_item_type", item == null ? null : item.get("type"));
				m.put("quantity", c.get("quantity"));
				comps.add(m);
			}
			r.put("components", comps);
			if (locations) {
				r.put("locations", locations(slotStocks.getOrDefault(p.id(), List.of()), slots, storages));
			}
			r.put("created_at", p.get("created_at"));
			r.put("updated_at", p.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** Depo > raf > adet; depo ve raf adina gore (PHP dizi karsilastirmasi, kararli siralama). */
	private static List<Map<String, Object>> locations(List<Row> stocks, Map<Long, Row> slots, Map<Long, Row> storages) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Row s : stocks) {
			Row slot = slots.get(s.longValue("finished_product_storage_slot_id"));
			Row storage = slot == null ? null : storages.get(slot.longValue("finished_product_storage_id"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("storage_id", slot == null ? null : slot.get("finished_product_storage_id"));
			m.put("storage_name", storage == null ? null : storage.get("name"));
			m.put("slot_id", s.get("finished_product_storage_slot_id"));
			m.put("slot_name", slot == null ? null : slot.get("name"));
			m.put("quantity", s.get("quantity"));
			rows.add(m);
		}
		Comparator<Map<String, Object>> byStorage = (a, b) -> phpCompare(a.get("storage_name"), b.get("storage_name"));
		rows.sort(byStorage.thenComparing((a, b) -> phpCompare(a.get("slot_name"), b.get("slot_name"))));
		return rows;
	}

	/** PHP 8 {@code <=>} iki metin (ya da null) icin: ikisi de sayisal metinse sayi olarak, degilse bayt sirasi. */
	static int phpCompare(Object a, Object b) {
		String x = a == null ? "" : a.toString();
		String y = b == null ? "" : b.toString();
		if (LaravelValidator.numeric(x) && LaravelValidator.numeric(y)) {
			return Double.compare(Double.parseDouble(x.strip()), Double.parseDouble(y.strip()));
		}
		return Integer.signum(x.compareTo(y));
	}

}
