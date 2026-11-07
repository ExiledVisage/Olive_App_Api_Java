package com.olivamore.api.packaging.supplier;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Row;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductSupplierResource}; {@code lots} yalnizca detayda yuklu. */
final class ProductSupplierResource {

	private ProductSupplierResource() {
	}

	/** @param lots detayda lotlar (kalemiyle), listede null */
	static Map<String, Object> of(Row s, List<Map<String, Object>> lots) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", s.get("id"));
		r.put("name", s.get("name"));
		r.put("code", s.get("code"));
		r.put("phone", s.get("phone"));
		r.put("email", s.get("email"));
		r.put("address", s.get("address"));
		r.put("note", s.get("note"));
		r.put("is_active", Casts.bool(s.get("is_active")));
		r.put("lot_count", s.get("lots_count"));
		if (lots != null) {
			r.put("lots", lots);
		}
		r.put("created_at", s.get("created_at"));
		r.put("updated_at", s.get("updated_at"));
		return r;
	}

}
