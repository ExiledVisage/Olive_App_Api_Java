package com.olivamore.api.finishedproduct;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.InputNormalizationFilter;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code FinishedProductController} + {@code FinishedProductService}: bitmis urunler, ambalaj bilesenleri,
 * depo dokumu (rafta, sevkiyat bekleyen, acikta).
 */
@RestController
class FinishedProductController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");
	private static final String STORED = "(select sum(finished_product_storage_stocks.quantity) from finished_product_storage_stocks"
		+ " where finished_products.id = finished_product_storage_stocks.finished_product_id) as stored_stock";
	private static final String AWAITING = "(select sum(finished_product_stock_movements.quantity) from finished_product_stock_movements"
		+ " where finished_products.id = finished_product_stock_movements.finished_product_id and movement_type = 'order_pick'"
		+ " and exists (select * from orders where finished_product_stock_movements.order_id = orders.id and status = 'ready'))"
		+ " as awaiting_shipment_stock";

	private final Db db;
	private final LaravelValidator validator;

	FinishedProductController(Db db, LaravelValidator validator) {
		this.db = db;
		this.validator = validator;
	}

	/** {@code ?is_active}; en yeni once; tarif, bilesenler, raf konumlari ve depo toplamlari yuklu. */
	@GetMapping("/api/finished-products")
	@RequiresPermission("finished_product.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		StringBuilder sql = new StringBuilder("select finished_products.*, " + STORED + ", " + AWAITING + " from finished_products");
		List<Object> args = new ArrayList<>();
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			String value = request.getParameter("is_active");
			sql.append(" where is_active = ?");
			args.add(value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT)));
		}
		sql.append(" order by created_at desc");
		return new ListResponse.Plain<>(resources(db.select(sql.toString(), args.toArray()), false, true, true));
	}

	/** Detay: tarifin icerigi de. */
	@GetMapping("/api/finished-products/{product}")
	@RequiresPermission("finished_product.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "product", table = "finished_products", model = "FinishedProduct") Row product) {
		Row row = db.first("select finished_products.*, " + STORED + ", " + AWAITING + " from finished_products where id = ?",
			product.id()).orElseThrow();
		return DataResponse.of(resources(List.of(row), true, true, true).getFirst());
	}

	/** 201; cevapta depo toplami var, sevkiyat bekleyen ve konumlar yok (Laravel'de yuklenmiyor). */
	@PostMapping("/api/finished-products")
	@RequiresPermission("finished_product.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, rules("required", "required"));
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("finished_product_recipe_id", v.get("finished_product_recipe_id"));
		values.put("volume_litre", v.get("volume_litre"));
		values.put("total_stock", 0);
		values.put("is_active", v.get("is_active") == null ? true : v.get("is_active"));
		long id = db.insert("finished_products", values);
		syncComponents(id, RecipeController.items(v.get("components")));
		return DataResponse.created(written(id));
	}

	/** Fabrikada sisesi olan urunun hacmi degistirilemez. Verilen bilesenler eskilerin yerine gecer. */
	@RequestMapping(path = "/api/finished-products/{product}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("finished_product.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "product", table = "finished_products", model = "FinishedProduct") Row product,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, rules("sometimes", "required_with:components"));
		Map<String, Object> data = v.all();
		if (data.containsKey("volume_litre")
			&& Math.abs(Php.floatval(data.get("volume_litre")) - Php.floatval(product.get("volume_litre"))) > 0.0001
			&& Php.intval(product.get("total_stock")) > 0) {
			throw new BusinessRuleException("'" + product.string("name") + "' ürününün fabrikada " + product.get("total_stock")
				+ " şişesi var; hacmi değiştirilemez. Farklı hacim için yeni ürün tanımlayın.");
		}
		Map<String, Object> changes = new LinkedHashMap<>();
		for (String key : List.of("name", "finished_product_recipe_id", "volume_litre", "is_active")) {
			if (data.get(key) != null) {
				// decimal:4 cast'i: 4 basamakta esit olan deger degisiklik sayilmaz
				changes.put(key, key.equals("volume_litre") ? Casts.decimal(data.get(key), 4) : data.get(key));
			}
		}
		db.update("finished_products", product.id(), changes);
		if (data.get("components") != null) {
			db.execute("delete from finished_product_components where finished_product_id = ?", product.id());
			syncComponents(product.id(), RecipeController.items(data.get("components")));
		}
		return DataResponse.of(written(product.id()));
	}

	/**
	 * Dolum kaydi olan urun silinmez (Laravel mesaji). Laravel siparis kalemi, raf ve depo kaydi olan urunde
	 * restrict FK yuzunden 500 veriyordu; burada kullanim yerleriyle 422 (DEVIATIONS).
	 */
	@DeleteMapping("/api/finished-products/{product}")
	@RequiresPermission("finished_product.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "product", table = "finished_products", model = "FinishedProduct") Row product) {
		long id = product.id();
		if (db.count("select count(*) from finished_product_runs where finished_product_id = ?", id) > 0) {
			throw new BusinessRuleException("Bu son ürüne ait dolum kaydı bulunduğu için silinemez.");
		}
		DeletionGuard.of("Bu son ürün")
			.usedBy("sipariş kalemi", db.count("select count(*) from order_items where finished_product_id = ?", id))
			.usedBy("depo hareketi", db.count("select count(*) from finished_product_stock_movements where finished_product_id = ?", id))
			.usedBy("raf stoğu", db.count("select count(*) from finished_product_storage_slot_stocks where finished_product_id = ?", id))
			.usedBy("depo stoğu", db.count("select count(*) from finished_product_storage_stocks where finished_product_id = ?", id))
			.check();
		db.delete("finished_products", id);
		return Map.of("message", "Son ürün başarıyla silindi.");
	}

	// ---- yardimcilar -------------------------------------------------------------

	/** Laravel kurallari; {@code distinct} Java'da eklendi: ayni kalem iki kez gelince unique kisiti 500 veriyordu (DEVIATIONS). */
	private static Rules rules(String presence, String itemPresence) {
		return Rules.of()
			.field("name", presence + "|string|max:255")
			.field("finished_product_recipe_id", presence + "|integer|exists:finished_product_recipes,id")
			.field("volume_litre", presence + "|numeric|min:0.001")
			.field("is_active", (presence.equals("required") ? "nullable" : "sometimes") + "|boolean")
			.field("components", presence + "|array|min:1")
			.field("components.*.packaging_item_id", itemPresence + "|integer|distinct|exists:packaging_items,id")
			.field("components.*.quantity", itemPresence + "|integer|min:1");
	}

	private void syncComponents(long productId, List<Map<String, Object>> components) {
		for (Map<String, Object> c : components) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("packaging_item_id", c.get("packaging_item_id"));
			values.put("quantity", c.get("quantity"));
			values.put("finished_product_id", productId);
			db.insert("finished_product_components", values);
		}
	}

	/** Ekleme/guncelleme cevabi: tarif (icerik yok), bilesenler, {@code loadSum} depo toplami. */
	private Map<String, Object> written(long id) {
		Row row = db.first("select finished_products.*, " + STORED + " from finished_products where id = ?", id).orElseThrow();
		return resources(List.of(row), false, false, false).getFirst();
	}

	/**
	 * FinishedProductResource.
	 * @param recipeContents tarifin icerigi yuklu mu (detay)
	 * @param awaiting       sevkiyat bekleyen toplam yuklu mu
	 * @param locations      raf konumlari yuklu mu
	 */
	private List<Map<String, Object>> resources(List<Row> products, boolean recipeContents, boolean awaiting, boolean locations) {
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
				rec.put("contents", RecipeController.contents(contents.getOrDefault(recipe.id(), List.of()), types));
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
