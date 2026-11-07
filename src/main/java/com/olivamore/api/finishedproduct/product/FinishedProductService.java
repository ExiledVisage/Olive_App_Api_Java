package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.ValidatedInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code FinishedProductService}: bitmis urunler ve ambalaj bilesenleri. */
@Service
class FinishedProductService {

	private static final String STORED = "(select sum(finished_product_storage_stocks.quantity) from finished_product_storage_stocks"
		+ " where finished_products.id = finished_product_storage_stocks.finished_product_id) as stored_stock";

	private static final String AWAITING = "(select sum(finished_product_stock_movements.quantity) from finished_product_stock_movements"
		+ " where finished_products.id = finished_product_stock_movements.finished_product_id and movement_type = 'order_pick'"
		+ " and exists (select * from orders where finished_product_stock_movements.order_id = orders.id and status = 'ready'))"
		+ " as awaiting_shipment_stock";

	private final Db db;

	FinishedProductService(Db db) {
		this.db = db;
	}

	/** {@code getAll}: {@code isActive} verilirse suzulur; en yeni once; depo toplamlari yuklu. */
	List<Row> all(Boolean isActive) {
		StringBuilder sql = new StringBuilder("select finished_products.*, " + STORED + ", " + AWAITING + " from finished_products");
		List<Object> args = new ArrayList<>();
		if (isActive != null) {
			sql.append(" where is_active = ?");
			args.add(isActive);
		}
		sql.append(" order by created_at desc");
		return db.select(sql.toString(), args.toArray());
	}

	/** {@code getById}: depo toplamlari yuklu. */
	Row byId(long id) {
		return db.first("select finished_products.*, " + STORED + ", " + AWAITING + " from finished_products where id = ?", id).orElseThrow();
	}

	/** Ekleme/guncelleme cevabi: yalnizca {@code loadSum} depo toplami. */
	Row written(long id) {
		return db.first("select finished_products.*, " + STORED + " from finished_products where id = ?", id).orElseThrow();
	}

	/** @return yeni urun id */
	long create(ValidatedInput v) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("finished_product_recipe_id", v.get("finished_product_recipe_id"));
		values.put("volume_litre", v.get("volume_litre"));
		values.put("total_stock", 0);
		values.put("is_active", v.get("is_active") == null ? true : v.get("is_active"));
		long id = db.insert("finished_products", values);
		syncComponents(id, items(v.get("components")));
		return id;
	}

	/** Fabrikada sisesi olan urunun hacmi degistirilemez. Verilen bilesenler eskilerin yerine gecer. */
	void update(Row product, ValidatedInput v) {
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
			syncComponents(product.id(), items(data.get("components")));
		}
	}

	/**
	 * Dolum kaydi olan urun silinmez (Laravel mesaji). Laravel siparis kalemi, raf ve depo kaydi olan urunde
	 * restrict FK yuzunden 500 veriyordu; burada kullanim yerleriyle 422 (DEVIATIONS 14).
	 */
	void delete(Row product) {
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

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> items(Object value) {
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>((Collection<Map<String, Object>>) m.values());
		}
		return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

}
