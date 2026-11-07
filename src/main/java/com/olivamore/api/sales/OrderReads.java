package com.olivamore.api.sales;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code OrderResource}, {@code OrderItemResource} ve {@code OrderService::stockCheck}. */
@Component
class OrderReads {

	/** {@code OrderService::TRANSITIONS} */
	static final Map<String, List<String>> TRANSITIONS = Map.of(
		"draft", List.of("confirmed", "cancelled"),
		"confirmed", List.of("stock_check", "cancelled"),
		"stock_check", List.of("ready", "in_production", "cancelled"),
		"in_production", List.of("ready", "cancelled"),
		"ready", List.of("shipped", "cancelled"));

	private final Db db;

	OrderReads(Db db) {
		this.db = db;
	}

	Map<String, Object> detail(long orderId) {
		return orders(List.of(db.find("orders", orderId).orElseThrow()), true).getFirst();
	}

	/**
	 * @param detail {@code DETAIL_RELATIONS}: olusturan kullanici ve durum gecmisi de (liste: musteri, adres,
	 *               kalemler)
	 */
	List<Map<String, Object>> orders(List<Row> orders, boolean detail) {
		List<Long> ids = Relations.ids(orders);
		Map<Long, Row> customers = Relations.byId(db.whereIn("customers", "id", Relations.column(orders, "customer_id"), null));
		Map<Long, Row> addresses = Relations.byId(db.whereIn("customer_addresses", "id", Relations.column(orders, "delivery_address_id"), null));
		List<Row> itemRows = db.whereIn("order_items", "order_id", ids, null);
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(itemRows, "finished_product_id"), null));
		Map<Long, List<Row>> items = Relations.groupBy(itemRows, "order_id");
		Map<Long, Row> users = Map.of();
		Map<Long, List<Row>> histories = Map.of();
		if (detail && !ids.isEmpty()) {
			List<Row> historyRows = db.select("select * from order_status_histories where order_id in (" + Relations.inList(ids)
				+ ") order by changed_at asc, id asc");
			List<Long> userIds = new ArrayList<>(Relations.column(orders, "created_by_user_id"));
			userIds.addAll(Relations.column(historyRows, "changed_by_user_id"));
			users = Relations.byId(db.whereIn("users", "id", userIds.stream().distinct().toList(), null));
			histories = Relations.groupBy(historyRows, "order_id");
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row o : orders) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", o.get("id"));
			r.put("status", o.get("status"));
			r.put("allowed_transitions", TRANSITIONS.getOrDefault(o.string("status"), List.of()));
			r.put("order_date", startOfDay(o.get("order_date")));
			r.put("note", o.get("note"));
			Row customer = customers.get(o.longValue("customer_id"));
			r.put("customer", customer == null ? null : customer(customer));
			Row address = addresses.get(o.longValue("delivery_address_id"));
			r.put("delivery_address", address == null ? null : CustomerController.address(address));
			List<Map<String, Object>> itemList = new ArrayList<>();
			for (Row item : items.getOrDefault(o.id(), List.of())) {
				Row product = products.get(item.longValue("finished_product_id"));
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", item.get("id"));
				m.put("finished_product", product == null ? null : product(product));
				m.put("quantity", item.get("quantity"));
				itemList.add(m);
			}
			r.put("items", itemList);
			if (detail) {
				Row creator = o.longValue("created_by_user_id") == null ? null : users.get(o.longValue("created_by_user_id"));
				r.put("created_by_user", creator == null ? null : idName(creator));
				List<Map<String, Object>> history = new ArrayList<>();
				for (Row h : histories.getOrDefault(o.id(), List.of())) {
					Row user = h.longValue("changed_by_user_id") == null ? null : users.get(h.longValue("changed_by_user_id"));
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("from_status", h.get("from_status"));
					m.put("to_status", h.get("to_status"));
					m.put("changed_by", user == null ? null : idName(user));
					m.put("note", h.get("note"));
					m.put("changed_at", h.get("changed_at"));
					history.add(m);
				}
				r.put("status_history", history);
			}
			r.put("created_at", o.get("created_at"));
			r.put("updated_at", o.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** CustomerResource, adresler yuklu degil. */
	private static Map<String, Object> customer(Row c) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", c.get("id"));
		r.put("name", c.get("name"));
		r.put("company_name", c.get("company_name"));
		r.put("phone", c.get("phone"));
		r.put("email", c.get("email"));
		r.put("notes", c.get("notes"));
		r.put("is_active", Casts.bool(c.get("is_active")));
		r.put("created_at", c.get("created_at"));
		r.put("updated_at", c.get("updated_at"));
		return r;
	}

	/** FinishedProductResource, hicbir iliski ya da toplam yuklu degil (depo alanlari null). */
	private static Map<String, Object> product(Row p) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", p.get("id"));
		r.put("name", p.get("name"));
		r.put("volume_litre", Casts.decimal(p.get("volume_litre"), 4));
		r.put("total_stock", p.get("total_stock"));
		r.put("stored_stock", null);
		r.put("awaiting_shipment_stock", null);
		r.put("unstored_stock", null);
		r.put("is_active", Casts.bool(p.get("is_active")));
		r.put("created_at", p.get("created_at"));
		r.put("updated_at", p.get("updated_at"));
		return r;
	}

	private static Map<String, Object> idName(Row row) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", row.get("id"));
		m.put("name", row.get("name"));
		return m;
	}

	/** {@code date} cast'i: gunun basi (UTC). */
	static LocalDateTime startOfDay(Object value) {
		return switch (value) {
			case null -> null;
			case LocalDate d -> d.atStartOfDay();
			case LocalDateTime t -> t.toLocalDate().atStartOfDay();
			case java.sql.Date d -> d.toLocalDate().atStartOfDay();
			default -> LocalDate.parse(Php.strval(value).substring(0, 10)).atStartOfDay();
		};
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

}
