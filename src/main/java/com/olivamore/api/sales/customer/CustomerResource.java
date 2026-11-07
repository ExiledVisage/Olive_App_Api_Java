package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code CustomerResource}. */
@Component
public class CustomerResource {

	private final Db db;

	CustomerResource(Db db) {
		this.db = db;
	}

	/** Adresler yuklu (Laravel eager loading sirasiyla). */
	List<Map<String, Object>> withAddresses(List<Row> customers) {
		Map<Long, List<Row>> addresses = Relations.groupBy(
			db.whereIn("customer_addresses", "customer_id", Relations.ids(customers), null), "customer_id");
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row c : customers) {
			out.add(of(c, addresses.getOrDefault(c.id(), List.of()).stream().map(CustomerAddressResource::of).toList()));
		}
		return out;
	}

	/** Adresler yuklu degil ({@code addresses} anahtari yok); siparis kullanir. */
	public static Map<String, Object> withoutAddresses(Row c) {
		return of(c, null);
	}

	private static Map<String, Object> of(Row c, List<Map<String, Object>> addresses) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", c.get("id"));
		r.put("name", c.get("name"));
		r.put("company_name", c.get("company_name"));
		r.put("phone", c.get("phone"));
		r.put("email", c.get("email"));
		r.put("notes", c.get("notes"));
		r.put("is_active", Casts.bool(c.get("is_active")));
		if (addresses != null) {
			r.put("addresses", addresses);
		}
		r.put("created_at", c.get("created_at"));
		r.put("updated_at", c.get("updated_at"));
		return r;
	}

}
