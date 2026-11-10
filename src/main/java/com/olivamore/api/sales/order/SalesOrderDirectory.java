package com.olivamore.api.sales.order;

import com.olivamore.api.finishedproduct.OrderDirectory;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Bitmis urun modulunun stok hesabinda ve silme kontrolunde istedigi siparis bilgileri. */
@Component
class SalesOrderDirectory implements OrderDirectory {

	private final JdbcClient jdbc;

	SalesOrderDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<Long> readyAmong(Collection<Long> orderIds) {
		List<Long> present = ValidationErrors.present(orderIds);
		if (present.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("select id from orders where id in (:ids) and status = 'ready'")
			.param("ids", present).query(Long.class).list());
	}

	@Override
	public long itemCount(long productId) {
		return jdbc.sql("select count(*) from order_items where finished_product_id = ?").param(productId)
			.query(Long.class).single();
	}

}
