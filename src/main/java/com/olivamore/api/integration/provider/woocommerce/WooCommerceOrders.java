package com.olivamore.api.integration.provider.woocommerce;

import com.olivamore.api.integration.provider.ExternalOrder;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WooCommerce siparis cevabini {@link ExternalOrder}'a cevirir. Teslimat adresi {@code shipping}'ten, bossa
 * {@code billing}'den; e-posta ve telefon {@code billing}'de. Varyantsiz kalemde {@code variation_id} 0.
 */
final class WooCommerceOrders {

	/** Hazirlanmayi bekleyen (odemesi alinmis) siparis durumu. */
	static final String READY_STATUS = "processing";

	private WooCommerceOrders() {
	}

	static ExternalOrder order(Map<String, Object> order) {
		Map<?, ?> billing = map(order.get("billing"));
		Map<?, ?> shipping = map(order.get("shipping"));
		Map<?, ?> address = text(shipping.get("address_1")) != null ? shipping : billing;
		String name = join(" ", text(address.get("first_name")), text(address.get("last_name")));
		if (name == null) {
			name = join(" ", text(billing.get("first_name")), text(billing.get("last_name")));
		}
		ExternalOrder.Customer customer = new ExternalOrder.Customer(name == null ? "Site müşterisi" : name,
			text(address.get("company")), text(billing.get("email")), text(billing.get("phone")));
		ExternalOrder.Address delivery = new ExternalOrder.Address(text(address.get("country")),
			text(address.get("city")), text(address.get("state")),
			join(", ", text(address.get("address_1")), text(address.get("address_2"))), text(address.get("postcode")));
		List<ExternalOrder.Line> lines = new ArrayList<>();
		if (order.get("line_items") instanceof List<?> items) {
			for (Object item : items) {
				Map<?, ?> line = map(item);
				Object variation = line.get("variation_id");
				String variantId = variation == null || "0".equals(String.valueOf(variation)) ? ""
					: String.valueOf(variation);
				lines.add(new ExternalOrder.Line(String.valueOf(line.get("product_id")), variantId,
					text(line.get("name")), text(line.get("sku")), ((Number) line.get("quantity")).intValue(),
					text(line.get("id"))));
			}
		}
		return new ExternalOrder(String.valueOf(order.get("id")), text(order.get("number")),
			text(order.get("status")), date(order.get("date_created_gmt")), customer, delivery, lines,
			text(order.get("customer_note")));
	}

	private static LocalDateTime date(Object value) {
		String text = text(value);
		if (text == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(text.replace("Z", ""));
		} catch (DateTimeParseException e) {
			return null;
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

	private static String text(Object value) {
		if (value == null) {
			return null;
		}
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? null : s;
	}

}
