package com.olivamore.api.sales.invoice;

import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.sales.invoice.InvoiceMath.Amounts;
import com.olivamore.api.sales.invoice.InvoiceResponse.Line;
import com.olivamore.api.sales.invoice.InvoiceResponse.User;
import com.olivamore.api.sales.invoice.InvoiceResponse.VatGroup;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Fatura listesi (en yeni once) ve detayi. */
@Component
class InvoiceQueries {

	/** Bos filtre uygulanmaz. */
	record Filter(String status, Long customerId, Long orderId) {
	}

	private record InvoiceRow(Long id, String number, String status, LocalDate issueDate, Long orderId, String source,
		String externalRef, Long customerId, String buyerName, String buyerTaxNumber, String buyerTaxOffice,
		String buyerAddress, String buyerDistrict, String buyerCity, String buyerCountry, String buyerEmail,
		String buyerPhone, String currency, BigDecimal netTotal, BigDecimal vatTotal, BigDecimal grossTotal,
		String note, Long createdByUserId, LocalDateTime cancelledAt, Long cancelledByUserId, String cancelReason,
		LocalDateTime createdAt) {
	}

	private static final String SELECT = """
		select i.id, i.number, i.status, i.issue_date, i.order_id, o.source, o.external_ref, i.customer_id,
			i.buyer_name, i.buyer_tax_number, i.buyer_tax_office, i.buyer_address, i.buyer_district, i.buyer_city,
			i.buyer_country, i.buyer_email, i.buyer_phone, i.currency, i.net_total, i.vat_total, i.gross_total, i.note,
			i.created_by_user_id, i.cancelled_at, i.cancelled_by_user_id, i.cancel_reason, i.created_at
		from invoices i join orders o on o.id = i.order_id
		""";

	private static final String WHERE = """
		where (cast(:status as varchar) is null or i.status = :status)
			and (cast(:customer as bigint) is null or i.customer_id = :customer)
			and (cast(:order as bigint) is null or i.order_id = :order)
		""";

	private static final String ORDER = " order by i.issue_date desc, i.id desc";

	private final JdbcClient jdbc;
	private final UserDirectory users;

	InvoiceQueries(JdbcClient jdbc, UserDirectory users) {
		this.jdbc = jdbc;
		this.users = users;
	}

	@Transactional(readOnly = true)
	List<InvoiceResponse> list(Filter filter) {
		return responses(filtered(SELECT + WHERE + ORDER, filter).query(InvoiceRow.class).list());
	}

	@Transactional(readOnly = true)
	Page<InvoiceResponse> page(Filter filter, Pageable pageable) {
		long total = filtered("select count(*) from invoices i " + WHERE, filter).query(Long.class).single();
		List<InvoiceRow> rows = filtered(SELECT + WHERE + ORDER + " limit :limit offset :offset", filter)
			.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset()).query(InvoiceRow.class)
			.list();
		return new PageImpl<>(responses(rows), pageable, total);
	}

	@Transactional(readOnly = true)
	InvoiceResponse detail(long id) {
		InvoiceRow row = jdbc.sql(SELECT + " where i.id = :id").param("id", id).query(InvoiceRow.class).optional()
			.orElseThrow(() -> new ModelNotFoundException("Invoice", id));
		List<Line> lines = jdbc.sql("""
			select id, finished_product_id, description, quantity, unit_price, vat_rate, net_amount, vat_amount,
				gross_amount
			from invoice_lines where invoice_id = :id order by id
			""").param("id", id).query(Line.class).list();
		Map<Integer, List<Amounts>> byRate = lines.stream().collect(Collectors.groupingBy(Line::vatRate,
			Collectors.mapping(l -> new Amounts(l.grossAmount(), l.netAmount(), l.vatAmount()), Collectors.toList())));
		List<VatGroup> breakdown = InvoiceMath.byRate(byRate).stream()
			.map(g -> new VatGroup(g.rate(), g.net(), g.vat())).toList();
		return response(row, userMap(List.of(row)), breakdown, lines);
	}

	private JdbcClient.StatementSpec filtered(String sql, Filter filter) {
		String status = filter.status() == null || filter.status().isBlank() ? null : filter.status();
		return jdbc.sql(sql).param("status", status).param("customer", filter.customerId())
			.param("order", filter.orderId());
	}

	private List<InvoiceResponse> responses(List<InvoiceRow> rows) {
		Map<Long, User> userMap = userMap(rows);
		return rows.stream().map(row -> response(row, userMap, null, null)).toList();
	}

	private Map<Long, User> userMap(List<InvoiceRow> rows) {
		List<Long> ids = rows.stream().flatMap(r -> Stream.of(r.createdByUserId(), r.cancelledByUserId()))
			.filter(Objects::nonNull).distinct().toList();
		return users.users(ids).values().stream().collect(Collectors.toMap(UserDirectory.User::id,
			u -> new User(u.id(), u.name())));
	}

	private static InvoiceResponse response(InvoiceRow r, Map<Long, User> userMap, List<VatGroup> breakdown,
		List<Line> lines) {
		return new InvoiceResponse(r.id(), r.number(), r.status(), r.issueDate(),
			new InvoiceResponse.Order(r.orderId(), r.source(), r.externalRef()), r.customerId(),
			new InvoiceResponse.Buyer(r.buyerName(), r.buyerTaxNumber(), r.buyerTaxOffice(), r.buyerAddress(),
				r.buyerDistrict(), r.buyerCity(), r.buyerCountry(), r.buyerEmail(), r.buyerPhone()),
			r.currency(), r.netTotal(), r.vatTotal(), r.grossTotal(), r.note(), user(userMap, r.createdByUserId()),
			r.cancelledAt(), user(userMap, r.cancelledByUserId()), r.cancelReason(), breakdown,
			lines == null ? null : new ArrayList<>(lines), r.createdAt());
	}

	private static User user(Map<Long, User> userMap, Long id) {
		return id == null ? null : userMap.get(id);
	}

}
