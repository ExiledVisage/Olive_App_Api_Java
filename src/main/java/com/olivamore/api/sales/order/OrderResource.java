package com.olivamore.api.sales.order;

import com.olivamore.api.sales.customer.CustomerAddressResource;
import com.olivamore.api.sales.customer.CustomerResource;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code OrderResource} ve {@code OrderItemResource}. */
@Component
class OrderResource {

	private final Db db;

	OrderResource(Db db) {
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
			r.put("allowed_transitions", OrderService.allowedTransitions(o.string("status")));
			r.put("order_date", startOfDay(o.get("order_date")));
			r.put("note", o.get("note"));
			Row customer = customers.get(o.longValue("customer_id"));
			r.put("customer", customer == null ? null : CustomerResource.withoutAddresses(customer));
			Row address = addresses.get(o.longValue("delivery_address_id"));
			r.put("delivery_address", address == null ? null : CustomerAddressResource.of(address));
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

}
