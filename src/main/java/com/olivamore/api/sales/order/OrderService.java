package com.olivamore.api.sales.order;

import com.olivamore.api.finishedproduct.ShelfOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code OrderService}: siparis olusturma ve duzenleme, durum gecisleri, stok kontrolu. Hazirlamada (ready)
 * siseler raftan duser, sevkiyatta (shipped) fabrika stogundan, hazir siparis iptalinde raflara geri konur; hepsi
 * durum degisikligiyle ayni transaction'da.
 */
@Service
class OrderService {

	/** {@code OrderService::TRANSITIONS} */
	static final Map<String, List<String>> TRANSITIONS = Map.of(
		"draft", List.of("confirmed", "cancelled"),
		"confirmed", List.of("stock_check", "cancelled"),
		"stock_check", List.of("ready", "in_production", "cancelled"),
		"in_production", List.of("ready", "cancelled"),
		"ready", List.of("shipped", "cancelled"));

	private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Db db;
	private final ShelfOperations shelves;

	OrderService(Db db, ShelfOperations shelves) {
		this.db = db;
		this.shelves = shelves;
	}

	/** Siparisin bulundugu durumdan gecilebilecek durumlar. */
	static List<String> allowedTransitions(String status) {
		return TRANSITIONS.getOrDefault(status, List.of());
	}

	/** Taslak siparis; teslimat adresi musterinin olmali. @return yeni siparis id */
	long create(Map<String, Object> validated, long userId) {
		Map<String, Object> data = new LinkedHashMap<>(validated);
		Object items = data.remove("items");
		data.put("order_date", date(data.get("order_date")));
		data.put("status", "draft");
		data.put("created_by_user_id", userId);
		long id = db.insert("orders", data);
		assertDeliveryAddressBelongsToCustomer(db.find("orders", id).orElseThrow());
		createItems(id, items);
		recordStatusChange(id, null, "draft", userId, null);
		return id;
	}

	/** Yalnizca taslak ya da onayli siparis; verilen kalemler eskilerin yerine gecer. */
	void update(Row order, Map<String, Object> validated) {
		String status = order.string("status");
		if (!"draft".equals(status) && !"confirmed".equals(status)) {
			throw new BusinessRuleException("Sipariş '" + status + "' durumundayken düzenlenemez.");
		}
		Map<String, Object> data = new LinkedHashMap<>(validated);
		Object items = data.remove("items");
		if (!data.isEmpty()) {
			if (data.containsKey("order_date")) {
				data.put("order_date", date(data.get("order_date")));
			}
			db.update("orders", order.id(), data);
			assertDeliveryAddressBelongsToCustomer(db.find("orders", order.id()).orElseThrow());
		}
		if (items != null) {
			db.execute("delete from order_items where order_id = ? and order_id is not null", order.id());
			createItems(order.id(), items);
		}
	}

	/** Once stok isi, sonra durum: raftan alma, fabrika stogundan dusme, raflara geri koyma. */
	void updateStatus(Row order, String to, Object note, Object picks, long userId) {
		String from = order.string("status");
		if (!allowedTransitions(from).contains(to)) {
			throw new BusinessRuleException("'" + from + "' durumundan '" + to + "' durumuna geçiş yapılamaz.");
		}
		if ("ready".equals(to)) {
			pickFromShelves(order.id(), picks, userId);
		}
		if ("shipped".equals(to)) {
			releaseTotalStock(order.id());
		}
		if ("cancelled".equals(to) && "ready".equals(from)) {
			shelves.returnOrderPicks(order.id(), userId);
		}
		db.update("orders", order.id(), Map.of("status", to));
		recordStatusChange(order.id(), from, to, userId, note);
	}

	void delete(Row order) {
		if (!"draft".equals(order.string("status"))) {
			throw new BusinessRuleException("Sadece taslak (draft) durumdaki siparişler silinebilir.");
		}
		db.delete("orders", order.id());
	}

	// ---- stok kontrolu ---------------------------------------------------------------

	/**
	 * {@code stockCheck}: her kalem icin fabrikada yeterli sise var mi (baska hazir siparislerin borcu dusulerek)
	 * ve hangi depo/raftan alinir. Hicbir seyi kilitlemez.
	 */
	Map<String, Object> stockCheck(Row order) {
		List<Row> items = db.whereIn("order_items", "order_id", List.of(order.id()), null);
		List<Long> productIds = Relations.column(items, "finished_product_id");
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", productIds, null));
		Map<Long, Long> inStorages = sums("select finished_product_id, SUM(quantity) as total from finished_product_storage_stocks"
			+ " where finished_product_id in (%s) group by finished_product_id", productIds);
		Map<Long, Long> committed = sums("select finished_product_id, SUM(quantity) as total from order_items where finished_product_id in (%s)"
			+ " and exists (select * from orders where order_items.order_id = orders.id and status = 'ready' and id != " + order.id() + ")"
			+ " group by finished_product_id", productIds);
		Map<Long, Long> awaiting = sums("select finished_product_id, SUM(quantity) as total from finished_product_stock_movements"
			+ " where finished_product_id in (%s) and movement_type = 'order_pick' and exists (select * from orders"
			+ " where finished_product_stock_movements.order_id = orders.id and status = 'ready') group by finished_product_id", productIds);
		List<Row> stockRows = productIds.isEmpty() ? List.of() : db.select("select * from finished_product_storage_slot_stocks"
			+ " where finished_product_id in (" + Relations.inList(productIds) + ") and quantity > 0");
		Map<Long, Row> slots = Relations.byId(db.whereIn("finished_product_storage_slots", "id",
			Relations.column(stockRows, "finished_product_storage_slot_id"), null));
		Map<Long, Row> storages = Relations.byId(db.whereIn("finished_product_storages", "id",
			Relations.column(List.copyOf(slots.values()), "finished_product_storage_id"), null));
		Map<Long, List<Row>> locations = Relations.groupBy(stockRows, "finished_product_id");

		List<Map<String, Object>> out = new ArrayList<>();
		boolean fulfillable = true;
		for (Row item : items) {
			Row product = products.get(item.longValue("finished_product_id"));
			long requested = Php.intval(item.get("quantity"));
			long totalStock = Php.intval(product.get("total_stock"));
			long stored = inStorages.getOrDefault(product.id(), 0L);
			long reserved = committed.getOrDefault(product.id(), 0L);
			long available = Math.max(0, totalStock - reserved);
			long shortage = Math.max(0, requested - available);
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product.get("id"));
			p.put("name", product.get("name"));
			p.put("volume_litre", Php.floatval(Casts.decimal(product.get("volume_litre"), 4)));
			List<Row> productStocks = new ArrayList<>(locations.getOrDefault(product.id(), List.of()));
			Comparator<Row> byStorage = (a, b) -> phpCompare(storageName(a, slots, storages), storageName(b, slots, storages));
			productStocks.sort(byStorage
				.thenComparing((a, b) -> phpCompare(slotName(a, slots), slotName(b, slots)))
				.thenComparingLong(a -> a.longValue("finished_product_storage_slot_id")));
			List<Map<String, Object>> locs = new ArrayList<>();
			for (Row s : productStocks) {
				Row slot = slots.get(s.longValue("finished_product_storage_slot_id"));
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("storage_id", slot.get("finished_product_storage_id"));
				m.put("storage_name", storageName(s, slots, storages));
				m.put("slot_id", slot.get("id"));
				m.put("slot_name", slot.get("name"));
				m.put("quantity", Php.intval(s.get("quantity")));
				locs.add(m);
			}
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("order_item_id", item.get("id"));
			r.put("finished_product", p);
			r.put("requested", requested);
			r.put("total_stock", totalStock);
			r.put("in_storages", stored);
			r.put("unstored", Math.max(0, totalStock - stored - awaiting.getOrDefault(product.id(), 0L)));
			r.put("committed_to_ready_orders", reserved);
			r.put("available", available);
			r.put("shortage", shortage);
			r.put("is_sufficient", shortage == 0);
			r.put("locations", locs);
			fulfillable &= shortage == 0;
			out.add(r);
		}
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("order_id", order.id());
		data.put("status", order.get("status"));
		data.put("is_fulfillable", fulfillable);
		data.put("items", out);
		return data;
	}

	private Map<Long, Long> sums(String sql, List<Long> productIds) {
		Map<Long, Long> out = new LinkedHashMap<>();
		if (productIds.isEmpty()) {
			return out;
		}
		for (Row r : db.select(sql.formatted(Relations.inList(productIds)))) {
			out.put(r.longValue("finished_product_id"), Php.intval(r.get("total")));
		}
		return out;
	}

	private static String storageName(Row stock, Map<Long, Row> slots, Map<Long, Row> storages) {
		Row slot = slots.get(stock.longValue("finished_product_storage_slot_id"));
		Row storage = storages.get(slot.longValue("finished_product_storage_id"));
		return storage == null ? null : storage.string("name");
	}

	private static String slotName(Row stock, Map<Long, Row> slots) {
		String name = slots.get(stock.longValue("finished_product_storage_slot_id")).string("name");
		return name == null ? "" : name;
	}

	/** PHP 8 {@code <=>} iki metin icin: ikisi de sayisal metinse sayi olarak, degilse bayt sirasi. */
	private static int phpCompare(String a, String b) {
		String x = a == null ? "" : a;
		String y = b == null ? "" : b;
		if (com.olivamore.api.shared.validation.LaravelValidator.numeric(x) && com.olivamore.api.shared.validation.LaravelValidator.numeric(y)) {
			return Double.compare(Double.parseDouble(x.strip()), Double.parseDouble(y.strip()));
		}
		return Integer.signum(x.compareTo(y));
	}

	// ---- yardimcilar -----------------------------------------------------------------------

	private void pickFromShelves(long orderId, Object picks, long actorId) {
		List<Row> items = db.whereIn("order_items", "order_id", List.of(orderId), null);
		if (items.isEmpty()) {
			throw new BusinessRuleException("Kalemi olmayan sipariş hazırlanamaz.");
		}
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(items, "finished_product_id"), null));
		List<Map<String, Object>> given = items(picks);
		List<ShelfOperations.Pick> resolved = given.isEmpty() ? autoPicks(items, products) : validatePicks(items, products, given);
		shelves.pickForOrder(orderId, resolved, actorId);
	}

	/** Raf secimi yoksa: her kalem icin urunun rafa en eski girisinden baslayarak (FIFO). */
	private List<ShelfOperations.Pick> autoPicks(List<Row> items, Map<Long, Row> products) {
		List<ShelfOperations.Pick> resolved = new ArrayList<>();
		for (Row item : items) {
			long productId = item.longValue("finished_product_id");
			long remaining = Php.intval(item.get("quantity"));
			List<Row> stocks = new ArrayList<>(db.select("select * from finished_product_storage_slot_stocks where finished_product_id = ?"
				+ " and quantity > ?", productId, 0));
			Map<Long, String> firstIn = new LinkedHashMap<>();
			for (Row stock : stocks) {
				firstIn.put(stock.id(), firstStockInAt(stock.longValue("finished_product_storage_slot_id"), productId));
			}
			stocks.sort((a, b) -> firstIn.get(a.id()).compareTo(firstIn.get(b.id())));
			for (Row stock : stocks) {
				if (remaining <= 0) {
					break;
				}
				long take = Math.min(remaining, Php.intval(stock.get("quantity")));
				resolved.add(new ShelfOperations.Pick(stock.longValue("finished_product_storage_slot_id"), productId, take));
				remaining -= take;
			}
			if (remaining > 0) {
				throw new BusinessRuleException(productName(products, productId) + " için raflarda yeterli adet yok: " + remaining
					+ " adet eksik. Önce dolum yapılıp ürün rafa konulmalı.");
			}
		}
		return resolved;
	}

	/** Iscinin sectigi raflar: her secim bu siparisin bir kalemine ait, kalem basina toplam istenen adede esit. */
	private List<ShelfOperations.Pick> validatePicks(List<Row> items, Map<Long, Row> products, List<Map<String, Object>> picks) {
		Map<Long, Row> itemsById = Relations.byId(items);
		Map<Long, Long> perItem = new LinkedHashMap<>();
		List<ShelfOperations.Pick> resolved = new ArrayList<>();
		for (Map<String, Object> pick : picks) {
			long itemId = Php.intval(pick.get("order_item_id"));
			Row item = itemsById.get(itemId);
			if (item == null) {
				throw new BusinessRuleException("#" + itemId + " numaralı kalem bu siparişe ait değil.");
			}
			long quantity = Php.intval(pick.get("quantity"));
			perItem.merge(itemId, quantity, Long::sum);
			resolved.add(new ShelfOperations.Pick(Php.intval(pick.get("slot_id")), item.longValue("finished_product_id"), quantity));
		}
		for (Row item : items) {
			long picked = perItem.getOrDefault(item.id(), 0L);
			if (picked != Php.intval(item.get("quantity"))) {
				throw new BusinessRuleException(productName(products, item.longValue("finished_product_id")) + " için " + item.get("quantity")
					+ " adet gerekiyor, " + picked + " adet seçildi.");
			}
		}
		return resolved;
	}

	/** Sevkiyat: siseler fabrikadan cikar ({@code total_stock} duser); raf stogu hazirlamada zaten dusmustu. */
	private void releaseTotalStock(long orderId) {
		for (Row item : db.whereIn("order_items", "order_id", List.of(orderId), null)) {
			Row product = db.find("finished_products", item.longValue("finished_product_id")).orElse(null);
			if (product == null) {
				continue;
			}
			long quantity = Php.intval(item.get("quantity"));
			if (Php.intval(product.get("total_stock")) < quantity) {
				throw new BusinessRuleException("'" + product.string("name") + "' için fabrika stoğu (" + product.get("total_stock") + ") "
					+ "sevk edilecek adetten (" + item.get("quantity") + ") az; kayıtlar tutarsız.");
			}
			db.execute("update finished_products set total_stock = total_stock - ?, updated_at = ? where id = ?", quantity, Timestamps.now(),
				product.id());
		}
	}

	/** Rafta o urunun en eski giris zamani ({@code min(moved_at)} metni); hic giris yoksa en sona. */
	private String firstStockInAt(long slotId, long productId) {
		return db.first("select min(moved_at) as first from finished_product_stock_movements where finished_product_storage_slot_id = ?"
				+ " and finished_product_id = ? and movement_type in (?, ?, ?)", slotId, productId, "stock_in", "transfer_in", "order_return")
			.map(r -> r.get("first"))
			.map(v -> v instanceof LocalDateTime t ? SQL_TIME.format(t) : Php.strval(v))
			.orElse("9999-12-31 23:59:59");
	}

	private void createItems(long orderId, Object items) {
		for (Map<String, Object> item : items(items)) {
			Map<String, Object> values = new LinkedHashMap<>(item);
			values.put("order_id", orderId);
			db.insert("order_items", values);
		}
	}

	private void recordStatusChange(long orderId, String from, String to, long userId, Object note) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("from_status", from);
		values.put("to_status", to);
		values.put("changed_by_user_id", userId);
		values.put("note", note);
		values.put("changed_at", Timestamps.now());
		values.put("order_id", orderId);
		db.insert("order_status_histories", values);
	}

	private void assertDeliveryAddressBelongsToCustomer(Row order) {
		if (db.count("select count(*) from customer_addresses where customer_id = ? and customer_id is not null and id = ?",
			order.get("customer_id"), order.get("delivery_address_id")) == 0) {
			throw new BusinessRuleException("Teslimat adresi bu müşteriye ait değil.");
		}
	}

	private static String productName(Map<Long, Row> products, long productId) {
		Row product = products.get(productId);
		return product != null && product.get("name") != null ? product.string("name") : "#" + productId;
	}

	/** {@code date} cast'inin yazdigi deger. */
	private static LocalDateTime date(Object value) {
		return value == null ? null : PhpDates.parse(Php.strval(value)).orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>((Collection<Map<String, Object>>) m.values());
		}
		return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

}
