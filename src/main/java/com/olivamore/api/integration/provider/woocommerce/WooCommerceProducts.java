package com.olivamore.api.integration.provider.woocommerce;

import com.olivamore.api.integration.provider.ExternalProduct;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WooCommerce urun ve varyant cevabini {@link ExternalProduct}'a cevirir. Barkod once WooCommerce 9.2'nin cekirdek
 * alani {@code global_unique_id}'den (GTIN/UPC/EAN/ISBN), yoksa yaygin barkod eklentilerinin meta alanlarindan okunur.
 */
final class WooCommerceProducts {

	/** Barkod eklentilerinin kullandigi meta anahtarlari. */
	static final Set<String> BARCODE_META = Set.of("_ean", "_barcode", "_gtin", "ean", "barcode", "gtin",
		"_alg_ean", "hwp_product_gtin", "_wpm_gtin_code", "_global_unique_id");

	private WooCommerceProducts() {
	}

	static boolean isVariable(Map<String, Object> product) {
		return "variable".equals(product.get("type"));
	}

	static String id(Map<String, Object> product) {
		return String.valueOf(product.get("id"));
	}

	static ExternalProduct simple(Map<String, Object> product) {
		return new ExternalProduct(id(product), "", text(product.get("name")), text(product.get("sku")),
			barcode(product), stock(product));
	}

	/** Varyantin adi "Urun - Ozellik" (or. "Gemlik Sizma - 1 L"); SKU ve barkod varyanttan. */
	static ExternalProduct variation(Map<String, Object> parent, Map<String, Object> variation) {
		String attributes = attributes(variation);
		String name = text(parent.get("name")) + (attributes.isEmpty() ? "" : " - " + attributes);
		return new ExternalProduct(id(parent), id(variation), name, text(variation.get("sku")), barcode(variation),
			stock(variation));
	}

	static String barcode(Map<String, Object> item) {
		String core = text(item.get("global_unique_id"));
		if (core != null) {
			return core;
		}
		if (item.get("meta_data") instanceof List<?> meta) {
			for (Object entry : meta) {
				if (entry instanceof Map<?, ?> m && BARCODE_META.contains(String.valueOf(m.get("key")))) {
					String value = text(m.get("value"));
					if (value != null) {
						return value;
					}
				}
			}
		}
		return null;
	}

	/** Stok yonetimi kapaliysa null. */
	static Integer stock(Map<String, Object> item) {
		if (!Boolean.TRUE.equals(item.get("manage_stock")) || item.get("stock_quantity") == null) {
			return null;
		}
		return ((Number) item.get("stock_quantity")).intValue();
	}

	private static String attributes(Map<String, Object> variation) {
		if (!(variation.get("attributes") instanceof List<?> list)) {
			return "";
		}
		return String.join(", ", list.stream().filter(a -> a instanceof Map<?, ?>)
			.map(a -> String.valueOf(((Map<?, ?>) a).get("option"))).toList());
	}

	private static String text(Object value) {
		if (value == null) {
			return null;
		}
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? null : s;
	}

}
