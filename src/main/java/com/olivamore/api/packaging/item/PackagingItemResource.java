package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;

import java.util.LinkedHashMap;
import java.util.Map;

/** Laravel {@code PackagingItemResource} (lot sayilari yuklu; {@code lots} hicbir uc tarafindan yuklenmiyor). */
final class PackagingItemResource {

	private PackagingItemResource() {
	}

	static Map<String, Object> of(Row i) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", i.get("id"));
		r.put("name", i.get("name"));
		r.put("type", i.get("type"));
		r.put("current_stock", i.get("current_stock"));
		r.put("lot_count", i.get("lots_count"));
		r.put("active_lot_count", Php.intval(i.get("active_lot_count")));
		r.put("is_active", Casts.bool(i.get("is_active")));
		r.put("created_at", i.get("created_at"));
		r.put("updated_at", i.get("updated_at"));
		return r;
	}

}
