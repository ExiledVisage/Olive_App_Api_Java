package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;

import java.util.LinkedHashMap;
import java.util.Map;

/** Laravel {@code CustomerAddressResource} ({@code latitude}/{@code longitude}: float, {@code is_default}: boolean). Siparis de kullanir. */
public final class CustomerAddressResource {

	private static final String[] FIELDS = {"label", "country", "city", "district", "neighborhood", "street", "building_no",
		"apartment_no", "postal_code", "full_address", "latitude", "longitude", "is_default"};

	private CustomerAddressResource() {
	}

	public static Map<String, Object> of(Row a) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", a.get("id"));
		r.put("customer_id", a.get("customer_id"));
		for (String field : FIELDS) {
			Object value = a.get(field);
			r.put(field, switch (field) {
				case "latitude", "longitude" -> value == null ? null : Php.floatval(value);
				case "is_default" -> Casts.bool(value);
				default -> value;
			});
		}
		r.put("created_at", a.get("created_at"));
		r.put("updated_at", a.get("updated_at"));
		return r;
	}

}
