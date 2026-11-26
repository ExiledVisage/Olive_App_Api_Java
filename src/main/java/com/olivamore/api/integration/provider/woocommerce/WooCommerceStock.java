package com.olivamore.api.integration.provider.woocommerce;

import com.olivamore.api.integration.provider.StockUpdate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WooCommerce toplu guncelleme cevabi: {@code {"update": [...]}}; basarili kalem urunun kendisi, hatali kalem
 * {@code {"id": 12, "error": {"code": ..., "message": ...}}}. Cevapta olmayan kalem hatali sayilir.
 */
final class WooCommerceStock {

	private WooCommerceStock() {
	}

	static List<StockUpdate.Result> results(List<StockUpdate> sent, boolean variations, Map<?, ?> response) {
		Map<String, String> errors = new HashMap<>();
		Map<String, Boolean> seen = new HashMap<>();
		if (response != null && response.get("update") instanceof List<?> items) {
			for (Object item : items) {
				if (!(item instanceof Map<?, ?> m)) {
					continue;
				}
				String id = String.valueOf(m.get("id"));
				seen.put(id, true);
				if (m.get("error") instanceof Map<?, ?> error) {
					errors.put(id, String.valueOf(error.get("message")));
				}
			}
		}
		List<StockUpdate.Result> results = new ArrayList<>();
		for (StockUpdate update : sent) {
			String id = variations ? update.variantId() : update.productId();
			String error = errors.get(id);
			if (error == null && !seen.containsKey(id)) {
				error = "WooCommerce bu ürünün güncellendiğini bildirmedi.";
			}
			results.add(new StockUpdate.Result(update.productId(), update.variantId(), error));
		}
		return results;
	}

}
