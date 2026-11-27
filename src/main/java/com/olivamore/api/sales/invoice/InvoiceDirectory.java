package com.olivamore.api.sales.invoice;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Siparislerin gecerli faturasi: siparis cevabi ve siparisin duzenlenip iptal edilememesi icin. */
@Component
public class InvoiceDirectory {

	public record Summary(Long id, Long orderId, String number, LocalDate issueDate, BigDecimal grossTotal) {
	}

	private final JdbcClient jdbc;

	InvoiceDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Gecerli (iptal edilmemis) faturasi olmayan siparis haritada yok. */
	public Map<Long, Summary> issuedFor(Collection<Long> orderIds) {
		List<Long> present = ValidationErrors.present(orderIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select id, order_id, number, issue_date, gross_total from invoices
			where order_id in (:ids) and status = 'issued'
			""").param("ids", present).query(Summary.class).list().stream()
			.collect(Collectors.toMap(Summary::orderId, Function.identity()));
	}

	public Optional<Summary> issuedFor(long orderId) {
		return Optional.ofNullable(issuedFor(List.of(orderId)).get(orderId));
	}

}
