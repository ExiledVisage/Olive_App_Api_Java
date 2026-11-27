package com.olivamore.api.sales.invoice;

import com.olivamore.api.sales.SalesInvoiceCancelled;
import com.olivamore.api.sales.SalesInvoiceIssued;
import com.olivamore.api.sales.invoice.InvoiceMath.Amounts;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Siparisten fatura kesme ve iptal (docs/INVOICES.md). Onaylanmis, iptal edilmemis siparise kesilir; siparis basina
 * tek gecerli fatura. Fiyati olmayan kalem urunun liste fiyatini alir, o da yoksa fatura kesilmez. Numara yil icinde
 * sirali; ayni anda kesilen faturalar ayni numarayi alamaz (transaction kilidi).
 */
@Service
class InvoiceService {

	static final Set<String> INVOICEABLE = Set.of("confirmed", "stock_check", "in_production", "ready", "shipped");

	private record OrderRow(Long id, String status, Long customerId, String name, String companyName,
		String taxNumber, String taxOffice, String email, String phone, String fullAddress, String neighborhood,
		String street, String buildingNo, String apartmentNo, String district, String city, String country,
		String postalCode) {
	}

	private record ItemRow(Long finishedProductId, String name, int quantity, BigDecimal unitPrice, int vatRate,
		BigDecimal salePrice) {
	}

	private final JdbcClient jdbc;
	private final String prefix;
	private final ZoneId zone;
	private final ApplicationEventPublisher events;

	InvoiceService(JdbcClient jdbc, @Value("${olive.invoice.prefix:OLV}") String prefix,
		@Value("${olive.integration.zone:Europe/Istanbul}") String zone, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.events = events;
		this.prefix = prefix;
		this.zone = ZoneId.of(zone);
	}

	@Transactional
	long issue(long orderId, IssueInvoiceRequest request, long userId) {
		OrderRow order = jdbc.sql("""
			select o.id, o.status, o.customer_id, c.name, c.company_name, c.tax_number, c.tax_office, c.email, c.phone,
				a.full_address, a.neighborhood, a.street, a.building_no, a.apartment_no, a.district, a.city, a.country,
				a.postal_code
			from orders o
			join customers c on c.id = o.customer_id
			join customer_addresses a on a.id = o.delivery_address_id
			where o.id = :id
			for update of o
			""").param("id", orderId).query(OrderRow.class).optional()
			.orElseThrow(() -> new ModelNotFoundException("Order", orderId));
		if (!INVOICEABLE.contains(order.status())) {
			throw new BusinessRuleException("Sipariş '" + order.status() + "' durumundayken fatura kesilemez; "
				+ "önce siparişi onaylayın.");
		}
		jdbc.sql("select number from invoices where order_id = :order and status = 'issued'").param("order", orderId)
			.query(String.class).optional().ifPresent(number -> {
				throw new BusinessRuleException("Bu siparişin faturası zaten kesildi (" + number + ").");
			});
		List<ItemRow> items = jdbc.sql("""
			select i.finished_product_id, p.name, i.quantity, i.unit_price, i.vat_rate, p.sale_price
			from order_items i join finished_products p on p.id = i.finished_product_id
			where i.order_id = :order order by i.id
			""").param("order", orderId).query(ItemRow.class).list();
		if (items.isEmpty()) {
			throw new BusinessRuleException("Kalemi olmayan siparişe fatura kesilemez.");
		}
		List<String> unpriced = items.stream().filter(i -> price(i) == null).map(ItemRow::name).toList();
		if (!unpriced.isEmpty()) {
			throw new BusinessRuleException("Fiyatı olmayan ürün: " + String.join(", ", unpriced)
				+ ". Ürüne satış fiyatı girin ya da siparişte fiyat yazın.");
		}

		List<Amounts> amounts = new ArrayList<>();
		for (ItemRow item : items) {
			amounts.add(InvoiceMath.line(price(item), item.quantity(), item.vatRate()));
		}
		Amounts total = InvoiceMath.total(amounts);
		LocalDateTime now = Timestamps.now();
		LocalDate issueDate = request.issueDate() != null ? request.issueDate().toLocalDate()
			: now.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate();
		int year = issueDate.getYear();
		jdbc.sql("select pg_advisory_xact_lock(hashtext('invoice_number'))").query().listOfRows();
		int sequence = jdbc.sql("select coalesce(max(sequence), 0) + 1 from invoices where year = :year")
			.param("year", year).query(Integer.class).single();
		String number = prefix + year + String.format("%09d", sequence);

		long invoiceId = jdbc.sql("""
			insert into invoices (order_id, customer_id, number, year, sequence, status, issue_date, buyer_name,
				buyer_tax_number, buyer_tax_office, buyer_address, buyer_district, buyer_city, buyer_country,
				buyer_email, buyer_phone, net_total, vat_total, gross_total, note, created_by_user_id, created_at,
				updated_at)
			values (:order, :customer, :number, :year, :sequence, 'issued', :date, :name, :taxNumber, :taxOffice,
				:address, :district, :city, :country, :email, :phone, :net, :vat, :gross, :note, :user, :now, :now)
			returning id
			""").param("order", orderId).param("customer", order.customerId()).param("number", number)
			.param("year", year).param("sequence", sequence).param("date", issueDate)
			.param("name", order.companyName() != null ? order.companyName() : order.name())
			.param("taxNumber", order.taxNumber()).param("taxOffice", order.taxOffice())
			.param("address", address(order)).param("district", order.district()).param("city", order.city())
			.param("country", order.country()).param("email", order.email()).param("phone", order.phone())
			.param("net", total.net()).param("vat", total.vat()).param("gross", total.gross())
			.param("note", request.note()).param("user", userId).param("now", now).query(Long.class).single();
		for (int i = 0; i < items.size(); i++) {
			ItemRow item = items.get(i);
			Amounts line = amounts.get(i);
			jdbc.sql("""
				insert into invoice_lines (invoice_id, finished_product_id, description, quantity, unit_price, vat_rate,
					net_amount, vat_amount, gross_amount, created_at, updated_at)
				values (:invoice, :product, :description, :quantity, :price, :rate, :net, :vat, :gross, :now, :now)
				""").param("invoice", invoiceId).param("product", item.finishedProductId())
				.param("description", item.name()).param("quantity", item.quantity()).param("price", price(item))
				.param("rate", item.vatRate()).param("net", line.net()).param("vat", line.vat())
				.param("gross", line.gross()).param("now", now).update();
		}
		events.publishEvent(new SalesInvoiceIssued(invoiceId));
		return invoiceId;
	}

	/** Iptal edilen fatura numarasiyla kalir; siparise yeniden fatura kesilebilir. */
	@Transactional
	void cancel(long id, CancelInvoiceRequest request, long userId) {
		String status = jdbc.sql("select status from invoices where id = :id for update").param("id", id)
			.query(String.class).optional().orElseThrow(() -> new ModelNotFoundException("Invoice", id));
		if (!"issued".equals(status)) {
			throw new BusinessRuleException("Fatura zaten iptal edilmiş.");
		}
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			update invoices set status = 'cancelled', cancelled_at = :now, cancelled_by_user_id = :user,
				cancel_reason = :reason, updated_at = :now
			where id = :id
			""").param("now", now).param("user", userId).param("reason", request.reason()).param("id", id).update();
		events.publishEvent(new SalesInvoiceCancelled(id));
	}

	private static BigDecimal price(ItemRow item) {
		return item.unitPrice() != null ? item.unitPrice() : item.salePrice();
	}

	/** Acik adres yazilmissa o; yoksa mahalle, sokak, bina ve daire. Posta kodu sonda. */
	private static String address(OrderRow order) {
		List<String> parts = new ArrayList<>();
		if (order.fullAddress() != null && !order.fullAddress().isBlank()) {
			parts.add(order.fullAddress().trim());
		} else {
			add(parts, order.neighborhood());
			add(parts, order.street());
			add(parts, order.buildingNo() == null ? null : "No: " + order.buildingNo());
			add(parts, order.apartmentNo() == null ? null : "D: " + order.apartmentNo());
		}
		add(parts, order.postalCode());
		return parts.isEmpty() ? null : parts.stream().collect(Collectors.joining(", "));
	}

	private static void add(List<String> parts, String value) {
		if (value != null && !value.isBlank()) {
			parts.add(value.trim());
		}
	}

}
