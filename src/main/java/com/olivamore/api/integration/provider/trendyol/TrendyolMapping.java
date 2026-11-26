package com.olivamore.api.integration.provider.trendyol;

import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.ExternalProduct;
import com.olivamore.api.integration.provider.StockUpdate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trendyol cevaplarini platformdan bagimsiz bicime cevirir. Sayfali cevap: {@code totalPages}, {@code content[]}.
 * Siparis paketinin {@code orderDate}'i Trendyol'un belgesine gore GMT+3 duvar saatinin milisaniyesi; UTC'ye
 * cevirmek icin 3 saat dusulur.
 */
final class TrendyolMapping {

	/** {@code orderDate} Turkiye saatiyle (GMT+3) yazilmis. */
	private static final int ORDER_DATE_OFFSET_HOURS = 3;

	private TrendyolMapping() {
	}

	static List<Map<?, ?>> content(Map<?, ?> page) {
		List<Map<?, ?>> result = new ArrayList<>();
		if (page != null && page.get("content") instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof Map<?, ?> m) {
					result.add(m);
				}
			}
		}
		return result;
	}

	static int totalPages(Map<?, ?> page) {
		return page != null && page.get("totalPages") instanceof Number n ? n.intValue() : 1;
	}

	/** Arsivlenmis ya da barkodsuz urun alinmaz. */
	static ExternalProduct product(Map<?, ?> item) {
		String barcode = text(item.get("barcode"));
		if (barcode == null || Boolean.TRUE.equals(item.get("archived"))) {
			return null;
		}
		Integer quantity = item.get("quantity") instanceof Number n ? n.intValue() : null;
		return new ExternalProduct(barcode, "", text(item.get("title")) == null ? barcode : text(item.get("title")),
			text(item.get("stockCode")), barcode, quantity);
	}

	static ExternalOrder order(Map<?, ?> pkg) {
		Map<?, ?> address = map(pkg.get("shipmentAddress"));
		String name = join(" ", text(pkg.get("customerFirstName")), text(pkg.get("customerLastName")));
		if (name == null) {
			name = join(" ", text(address.get("firstName")), text(address.get("lastName")));
		}
		ExternalOrder.Customer customer = new ExternalOrder.Customer(name == null ? "Trendyol müşterisi" : name,
			text(address.get("company")), text(pkg.get("customerEmail")), text(address.get("phone")));
		String line = text(address.get("fullAddress"));
		if (line == null) {
			line = join(", ", text(address.get("address1")), text(address.get("address2")));
		}
		ExternalOrder.Address delivery = new ExternalOrder.Address(text(address.get("countryCode")),
			text(address.get("city")), text(address.get("district")), line, text(address.get("postalCode")));
		List<ExternalOrder.Line> lines = new ArrayList<>();
		if (pkg.get("lines") instanceof List<?> items) {
			for (Object item : items) {
				Map<?, ?> l = map(item);
				String barcode = text(l.get("barcode"));
				String sku = text(l.get("merchantSku")) != null ? text(l.get("merchantSku")) : text(l.get("stockCode"));
				lines.add(new ExternalOrder.Line(barcode == null ? "" : barcode, "", text(l.get("productName")), sku,
					l.get("quantity") instanceof Number n ? n.intValue() : 0));
			}
		}
		Object packageId = pkg.get("shipmentPackageId") != null ? pkg.get("shipmentPackageId") : pkg.get("id");
		return new ExternalOrder(String.valueOf(packageId), text(pkg.get("orderNumber")), text(pkg.get("status")),
			orderDate(pkg.get("orderDate")), customer, delivery, lines, null);
	}

	/** Kuyruktaki is henuz bitmedi mi (genel durum IN_PROGRESS ya da kalemlerden biri IN_PROGRESS). */
	static boolean inProgress(Map<?, ?> result) {
		if (result == null) {
			return true;
		}
		if ("IN_PROGRESS".equals(result.get("status"))) {
			return true;
		}
		if (result.get("items") instanceof List<?> items) {
			return items.stream().anyMatch(i -> i instanceof Map<?, ?> m && "IN_PROGRESS".equals(m.get("status")));
		}
		return false;
	}

	/** Kalem kalem sonuc; sonucta olmayan ya da hala islenen kalem gonderildi sayilir. */
	static List<StockUpdate.Result> stockResults(List<StockUpdate> sent, Map<?, ?> result) {
		Map<String, String> failures = new HashMap<>();
		if (result != null && result.get("items") instanceof List<?> items) {
			for (Object item : items) {
				Map<?, ?> m = map(item);
				String barcode = text(map(m.get("requestItem")).get("barcode"));
				if (barcode != null && "FAILED".equals(m.get("status"))) {
					List<?> reasons = m.get("failureReasons") instanceof List<?> r ? r : List.of();
					failures.put(barcode, reasons.isEmpty() ? "Trendyol güncellemeyi reddetti."
						: String.join(" · ", reasons.stream().map(String::valueOf).toList()));
				}
			}
		}
		return sent.stream().map(u -> new StockUpdate.Result(u.productId(), u.variantId(), failures.get(u.productId())))
			.toList();
	}

	private static LocalDateTime orderDate(Object value) {
		if (!(value instanceof Number millis)) {
			return null;
		}
		return LocalDateTime.ofEpochSecond(millis.longValue() / 1000, 0, ZoneOffset.UTC)
			.minusHours(ORDER_DATE_OFFSET_HOURS);
	}

	private static Map<?, ?> map(Object value) {
		return value instanceof Map<?, ?> m ? m : Map.of();
	}

	private static String join(String separator, String... parts) {
		List<String> present = new ArrayList<>();
		for (String part : parts) {
			if (part != null) {
				present.add(part);
			}
		}
		return present.isEmpty() ? null : String.join(separator, present);
	}

	private static String text(Object value) {
		if (value == null) {
			return null;
		}
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? null : s;
	}

}
