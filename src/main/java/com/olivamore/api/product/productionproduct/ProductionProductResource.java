package com.olivamore.api.product.productionproduct;

import com.olivamore.api.productionline.ProductionLineResponse;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Row;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionProductResource}. */
final class ProductionProductResource {

	private ProductionProductResource() {
	}

	/** Iliskisiz (store/update cevabi): hatlar bos. */
	static Map<String, Object> of(Row p) {
		return of(p, null, null, null, List.of());
	}

	/**
	 * @param count    {@code withCount}: null ise anahtar yok
	 * @param mappings attribute eslemeleri yuklendiyse (anahtar yazilir)
	 * @param types    urun tipleri yuklendiyse
	 * @param lines    girdi + cikti hatlari (tekrarsiz); her baglamda yazilir, yuklenmediyse bos
	 */
	static Map<String, Object> of(Row p, Long count, List<Map<String, Object>> mappings, List<Map<String, Object>> types,
		List<ProductionLineResponse> lines) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", p.get("id"));
		r.put("key", p.get("key"));
		r.put("name", p.get("name"));
		r.put("description", p.get("description"));
		r.put("default_density_kg_per_litre", Casts.decimal(p.get("default_density_kg_per_litre"), 4));
		if (count != null) {
			r.put("products_count", count);
		}
		if (mappings != null) {
			r.put("attribute_definitions", mappings);
		}
		if (types != null) {
			r.put("production_product_types", types);
		}
		r.put("production_lines", lines);
		return r;
	}

}
