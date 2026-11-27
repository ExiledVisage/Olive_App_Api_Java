package com.olivamore.api.integration.provider.parasut;

import com.olivamore.api.integration.provider.AccountingClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parasut JSON:API govdeleri ve cevaplari. Tek kayit {@code data} nesnesi, liste {@code data} dizisi, ek kayitlar
 * {@code included}. Resmilestirme isinin durumu {@code pending}/{@code running} (suruyor), {@code done} ya da
 * {@code error} (gerekceler {@code errors}). Resmi belgenin numara alani dogrulanmadi ({@code invoice_number}).
 */
final class ParasutMapping {

	private static final Set<String> FINISHED = Set.of("done", "error");

	private ParasutMapping() {
	}

	static Map<String, Object> contact(AccountingClient.Contact c) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("name", c.name());
		attributes.put("contact_type", c.company() ? "company" : "person");
		attributes.put("account_type", "customer");
		putIf(attributes, "tax_number", c.taxNumber());
		putIf(attributes, "tax_office", c.taxOffice());
		putIf(attributes, "email", c.email());
		putIf(attributes, "phone", c.phone());
		putIf(attributes, "address", c.address());
		putIf(attributes, "district", c.district());
		putIf(attributes, "city", c.city());
		return ParasutClient.document("contacts", attributes, null);
	}

	static Map<String, Object> product(AccountingClient.Product p) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("name", p.name());
		putIf(attributes, "code", p.code());
		attributes.put("vat_rate", p.vatRate());
		putIf(attributes, "list_price", p.salePrice());
		attributes.put("currency", ParasutClient.CURRENCY);
		return ParasutClient.document("products", attributes, null);
	}

	/** Kalem fiyati KDV haric; vadesi kesim gunu. */
	static Map<String, Object> salesInvoice(AccountingClient.SalesInvoice invoice) {
		AccountingClient.Contact buyer = invoice.buyer();
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("item_type", "invoice");
		attributes.put("description", invoice.description());
		attributes.put("issue_date", invoice.issueDate().toString());
		attributes.put("due_date", invoice.issueDate().toString());
		attributes.put("currency", ParasutClient.CURRENCY);
		putIf(attributes, "order_no", invoice.orderNo());
		putIf(attributes, "order_date", invoice.orderDate() == null ? null : invoice.orderDate().toString());
		putIf(attributes, "tax_number", buyer.taxNumber());
		putIf(attributes, "tax_office", buyer.taxOffice());
		putIf(attributes, "billing_address", buyer.address());
		putIf(attributes, "district", buyer.district());
		putIf(attributes, "city", buyer.city());
		List<Object> details = new ArrayList<>();
		for (AccountingClient.Line line : invoice.lines()) {
			Map<String, Object> detail = new LinkedHashMap<>();
			detail.put("type", "sales_invoice_details");
			detail.put("attributes", Map.of("quantity", line.quantity(), "unit_price", line.unitPrice(), "vat_rate",
				line.vatRate(), "description", line.description()));
			detail.put("relationships", Map.of("product", ParasutClient.ref("products", line.productId())));
			details.add(detail);
		}
		Map<String, Object> relationships = new LinkedHashMap<>();
		relationships.put("contact", ParasutClient.ref("contacts", invoice.contactId()));
		relationships.put("details", Map.of("data", details));
		return ParasutClient.document("sales_invoices", attributes, relationships);
	}

	/** Temel senaryo; alicinin e-fatura posta kutusuna. */
	static Map<String, Object> eInvoice(String salesInvoiceId, String address) {
		return ParasutClient.document("e_invoices", Map.of("scenario", "basic", "to", address),
			Map.of("invoice", ParasutClient.ref("sales_invoices", salesInvoiceId)));
	}

	static Map<String, Object> eArchive(String salesInvoiceId, AccountingClient.InternetSale sale) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		if (sale != null) {
			Map<String, Object> internet = new LinkedHashMap<>();
			internet.put("url", sale.url());
			internet.put("payment_type", sale.paymentType());
			internet.put("payment_platform", sale.paymentPlatform());
			internet.put("payment_date", sale.paymentDate().toString());
			attributes.put("internet_sale", internet);
		}
		return ParasutClient.document("e_archives", attributes,
			Map.of("sales_invoice", ParasutClient.ref("sales_invoices", salesInvoiceId)));
	}

	static AccountingClient.Job job(Map<?, ?> response) {
		Map<?, ?> attributes = attributes(data(response));
		String status = text(attributes.get("status"));
		List<String> errors = new ArrayList<>();
		if (attributes.get("errors") instanceof List<?> list) {
			list.stream().map(ParasutMapping::errorText).filter(e -> e != null).forEach(errors::add);
		}
		boolean finished = status != null && FINISHED.contains(status);
		return new AccountingClient.Job(finished, "error".equals(status), errors);
	}

	/** Faturanin {@code active_e_document} iliskisi ve {@code included} icindeki karsiligi. */
	static Optional<AccountingClient.EDocument> eDocument(Map<?, ?> response) {
		Map<?, ?> relationships = map(data(response).get("relationships"));
		Map<?, ?> ref = map(map(relationships.get("active_e_document")).get("data"));
		String id = text(ref.get("id"));
		String type = text(ref.get("type"));
		if (id == null) {
			return Optional.empty();
		}
		String number = null;
		if (response != null && response.get("included") instanceof List<?> included) {
			for (Object item : included) {
				Map<?, ?> m = map(item);
				if (id.equals(text(m.get("id"))) && (type == null || type.equals(text(m.get("type"))))) {
					number = text(attributes(m).get("invoice_number"));
				}
			}
		}
		return Optional.of(new AccountingClient.EDocument(type, id, number));
	}

	/** PDF cevabinda indirme adresi ({@code data.attributes.url}); hazir degilse bos. */
	static String pdfUrl(Map<?, ?> response) {
		return text(attributes(data(response)).get("url"));
	}

	static Map<?, ?> data(Map<?, ?> response) {
		return response == null ? Map.of() : map(response.get("data"));
	}

	static List<Map<?, ?>> list(Map<?, ?> response) {
		List<Map<?, ?>> result = new ArrayList<>();
		if (response != null && response.get("data") instanceof List<?> items) {
			items.stream().filter(i -> i instanceof Map<?, ?>).forEach(i -> result.add((Map<?, ?>) i));
		}
		return result;
	}

	static Map<?, ?> attributes(Map<?, ?> resource) {
		return map(resource.get("attributes"));
	}

	static Map<?, ?> meta(Map<?, ?> response) {
		return response == null ? Map.of() : map(response.get("meta"));
	}

	static String text(Object value) {
		if (value == null) {
			return null;
		}
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? null : s;
	}

	/** Hata metin ya da {@code {title, detail}} nesnesi olabilir. */
	private static String errorText(Object error) {
		if (error instanceof Map<?, ?> m) {
			String detail = text(m.get("detail"));
			return detail != null ? detail : text(m.get("title"));
		}
		return text(error);
	}

	private static Map<?, ?> map(Object value) {
		return value instanceof Map<?, ?> m ? m : Map.of();
	}

	private static void putIf(Map<String, Object> target, String key, Object value) {
		if (value != null && !(value instanceof String s && s.isBlank())) {
			target.put(key, value instanceof BigDecimal d ? d : value);
		}
	}

}
