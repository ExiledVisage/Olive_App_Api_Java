package com.olivamore.api.sales;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Kesilmis faturanin muhasebe programina gidecek hali (docs/INVOICES.md): alici kesildigi andaki haliyle, kalemler
 * KDV dahil fiyat ve ayrilmis net/KDV tutarlariyla. Fatura yoksa bos.
 */
@Component
public class SalesInvoiceDocuments {

	/**
	 * @param number      bizdeki numara (OLV2026000000001)
	 * @param status      issued, cancelled
	 * @param orderSource manual, woocommerce, trendyol, hepsiburada
	 */
	public record Document(long id, String number, String status, LocalDate issueDate, long orderId,
		String orderSource, String orderExternalRef, LocalDate orderDate, long customerId, String buyerName,
		String buyerTaxNumber, String buyerTaxOffice, String buyerAddress, String buyerDistrict, String buyerCity,
		String buyerCountry, String buyerEmail, String buyerPhone, BigDecimal netTotal, BigDecimal vatTotal,
		BigDecimal grossTotal, String note, List<Line> lines) {
	}

	/**
	 * @param sku       urunun stok kodu; bos olabilir
	 * @param unitPrice KDV dahil
	 */
	public record Line(long finishedProductId, String description, String sku, int quantity, BigDecimal unitPrice,
		int vatRate, BigDecimal netAmount, BigDecimal vatAmount, BigDecimal grossAmount) {
	}

	private record Head(long id, String number, String status, LocalDate issueDate, long orderId, String orderSource,
		String orderExternalRef, LocalDate orderDate, long customerId, String buyerName, String buyerTaxNumber,
		String buyerTaxOffice, String buyerAddress, String buyerDistrict, String buyerCity, String buyerCountry,
		String buyerEmail, String buyerPhone, BigDecimal netTotal, BigDecimal vatTotal, BigDecimal grossTotal,
		String note) {
	}

	private final JdbcClient jdbc;

	SalesInvoiceDocuments(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Document> document(long invoiceId) {
		return jdbc.sql("""
			select i.id, i.number, i.status, i.issue_date, i.order_id, o.source as order_source,
				o.external_ref as order_external_ref, o.order_date, i.customer_id, i.buyer_name, i.buyer_tax_number,
				i.buyer_tax_office, i.buyer_address, i.buyer_district, i.buyer_city, i.buyer_country, i.buyer_email,
				i.buyer_phone, i.net_total, i.vat_total, i.gross_total, i.note
			from invoices i join orders o on o.id = i.order_id
			where i.id = :id
			""").param("id", invoiceId).query(Head.class).optional().map(h -> new Document(h.id(), h.number(),
			h.status(), h.issueDate(), h.orderId(), h.orderSource(), h.orderExternalRef(), h.orderDate(),
			h.customerId(), h.buyerName(), h.buyerTaxNumber(), h.buyerTaxOffice(), h.buyerAddress(),
			h.buyerDistrict(), h.buyerCity(), h.buyerCountry(), h.buyerEmail(), h.buyerPhone(), h.netTotal(),
			h.vatTotal(), h.grossTotal(), h.note(), lines(invoiceId)));
	}

	private List<Line> lines(long invoiceId) {
		return jdbc.sql("""
			select l.finished_product_id, l.description, p.sku, l.quantity, l.unit_price, l.vat_rate, l.net_amount,
				l.vat_amount, l.gross_amount
			from invoice_lines l join finished_products p on p.id = l.finished_product_id
			where l.invoice_id = :id order by l.id
			""").param("id", invoiceId).query(Line.class).list();
	}

}
