package com.olivamore.api.integration.provider.hepsiburada;

import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.ExternalProduct;
import com.olivamore.api.integration.provider.Prices;
import com.olivamore.api.integration.provider.StockUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hepsiburada cevaplarini platformdan bagimsiz bicime cevirir. Sayfali cevap: {@code totalCount} ve
 * {@code listings[]} ya da {@code items[]}. Siparis listesi kalem kalemdir ({@code id} kalem numarasi, {@code sku}
 * Hepsiburada SKU'su); ayni {@code orderNumber}'li kalemler tek siparis olur. Alici ve teslimat adresi kalemdeki
 * {@code shippingAddress}'ten (yoksa {@code deliveryAddress}) okunur: il {@code city}, ilce {@code town}, mahalle
 * {@code district}. Gercek hesapla dogrulanacak; alan yoksa bos gecilir.
 */
final class HepsiburadaMapping {

	/** Saat dilimi yazilmamis siparis tarihi Turkiye saatidir. */
	private static final ZoneId LOCAL = ZoneId.of("Europe/Istanbul");

	/** Stok isinin bitmemis durumlari. */
	private static final Set<String> PENDING = Set.of("Ready", "InProgress", "PROCESSING");

	private HepsiburadaMapping() {
	}

	static List<Map<?, ?>> list(Map<?, ?> page, String field) {
		List<Map<?, ?>> result = new ArrayList<>();
		if (page != null && page.get(field) instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof Map<?, ?> m) {
					result.add(m);
				}
			}
		}
		return result;
	}

	static int total(Map<?, ?> page) {
		return page != null && page.get("totalCount") instanceof Number n ? n.intValue() : 0;
	}

	/**
	 * Ilan: urun id'si Hepsiburada SKU'su, eslestirme bizim SKU'muzla (merchantSku); ilanda urun adi ve barkod yok.
	 * Hepsiburada deposundan gonderilen ilan (stogu Hepsiburada tutar) alinmaz.
	 */
	static ExternalProduct product(Map<?, ?> listing) {
		String hbSku = text(listing.get("hepsiburadaSku"));
		if (hbSku == null || Boolean.TRUE.equals(listing.get("isFulfilledByHB"))) {
			return null;
		}
		String merchantSku = text(listing.get("merchantSku"));
		Integer stock = listing.get("availableStock") instanceof Number n ? n.intValue() : null;
		return new ExternalProduct(hbSku, "", merchantSku == null ? hbSku : merchantSku, merchantSku, null, stock);
	}

	/** Kalemleri siparis numarasina gore toplar; ilk kalem alici, adres ve tarihi verir. */
	static List<ExternalOrder> orders(List<Map<?, ?>> lines) {
		Map<String, List<Map<?, ?>>> byOrder = new LinkedHashMap<>();
		for (Map<?, ?> line : lines) {
			String number = text(line.get("orderNumber"));
			if (number != null) {
				byOrder.computeIfAbsent(number, k -> new ArrayList<>()).add(line);
			}
		}
		List<ExternalOrder> result = new ArrayList<>();
		byOrder.forEach((number, items) -> result.add(order(number, items)));
		return result;
	}

	private static ExternalOrder order(String number, List<Map<?, ?>> items) {
		Map<?, ?> first = items.getFirst();
		Map<?, ?> address = map(first.get("shippingAddress"));
		if (address.isEmpty()) {
			address = map(first.get("deliveryAddress"));
		}
		String name = text(first.get("customerName"));
		if (name == null) {
			name = text(address.get("name"));
		}
		if (name == null) {
			name = text(map(first.get("customer")).get("name"));
		}
		ExternalOrder.Customer customer = new ExternalOrder.Customer(name == null ? "Hepsiburada müşterisi" : name,
			null, text(address.get("email")), text(address.get("phoneNumber")));
		ExternalOrder.Address delivery = new ExternalOrder.Address(text(address.get("countryCode")),
			text(address.get("city")), text(address.get("town")),
			join(", ", text(address.get("address")), text(address.get("district"))), null);
		List<ExternalOrder.Line> lines = new ArrayList<>();
		for (Map<?, ?> item : items) {
			String sku = text(item.get("merchantSKU")) != null ? text(item.get("merchantSKU"))
				: text(item.get("merchantSku"));
			String hbSku = text(item.get("sku")) != null ? text(item.get("sku")) : text(item.get("hepsiburadaSku"));
			int quantity = item.get("quantity") instanceof Number n ? n.intValue() : 0;
			lines.add(new ExternalOrder.Line(hbSku == null ? "" : hbSku, "", text(item.get("name")), sku, quantity,
				text(item.get("id")), unitPrice(item, quantity)));
		}
		return new ExternalOrder(number, number, text(first.get("status")), orderDate(first.get("orderDate")),
			customer, delivery, lines, null);
	}

	/** KDV dahil: kalem toplami ({@code totalPrice}) varsa adede bolunur, yoksa {@code unitPrice}. */
	private static BigDecimal unitPrice(Map<?, ?> item, int quantity) {
		BigDecimal total = Prices.decimal(map(item.get("totalPrice")).get("amount"));
		if (total != null) {
			return Prices.money(Prices.perUnit(total, quantity));
		}
		return Prices.money(Prices.decimal(map(item.get("unitPrice")).get("amount")));
	}

	/** Ortak barkod cevabi: {@code data[]} etiket verileri. */
	static List<String> labels(Map<?, ?> response) {
		List<String> result = new ArrayList<>();
		if (response != null && response.get("data") instanceof List<?> items) {
			items.stream().map(HepsiburadaMapping::text).filter(t -> t != null).forEach(result::add);
		}
		return result;
	}

	/** Stok isi hala kuyrukta ya da isleniyor mu. */
	static boolean inProgress(Map<?, ?> result) {
		return result == null || PENDING.contains(text(result.get("status")));
	}

	/** Kalem kalem sonuc; hatalarda olmayan ya da hala islenen kalem gonderildi sayilir. */
	static List<StockUpdate.Result> stockResults(List<StockUpdate> sent, Map<?, ?> result) {
		Map<String, String> failures = new HashMap<>();
		if (result != null && result.get("errors") instanceof List<?> errors) {
			for (Object error : errors) {
				Map<?, ?> e = map(error);
				String hbSku = text(e.get("hepsiburadaSku"));
				if (hbSku != null) {
					List<?> reasons = e.get("errors") instanceof List<?> r ? r : List.of();
					failures.put(hbSku, reasons.isEmpty() ? "Hepsiburada güncellemeyi reddetti."
						: String.join(" · ", reasons.stream().map(String::valueOf).toList()));
				}
			}
		}
		return sent.stream().map(u -> new StockUpdate.Result(u.productId(), u.variantId(), failures.get(u.productId())))
			.toList();
	}

	/** ISO tarih; saat dilimi yazilmamissa Turkiye saati. UTC doner. */
	static LocalDateTime orderDate(Object value) {
		String text = text(value);
		if (text == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
		} catch (DateTimeParseException e) {
			try {
				return LocalDateTime.parse(text).atZone(LOCAL).withZoneSameInstant(ZoneOffset.UTC)
					.toLocalDateTime();
			} catch (DateTimeParseException ignored) {
				return null;
			}
		}
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

	static String text(Object value) {
		if (value == null) {
			return null;
		}
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? null : s;
	}

}
