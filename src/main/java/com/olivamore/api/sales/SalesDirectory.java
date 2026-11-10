package com.olivamore.api.sales;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Bildirimlerde gorunen siparis bilgileri. */
@Component
public class SalesDirectory {

	private final JdbcClient jdbc;

	SalesDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** "Siparis #id - musteri" etiketi; siparis yoksa bos. */
	public Optional<String> orderLabel(long id) {
		return jdbc.sql("""
			select 'Sipariş #' || o.id || ' - ' || coalesce(c.name, 'Müşteri')
			from orders o left join customers c on c.id = o.customer_id where o.id = :id
			""").param("id", id).query(String.class).optional();
	}

}
