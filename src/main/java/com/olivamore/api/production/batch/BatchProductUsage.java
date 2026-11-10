package com.olivamore.api.production.batch;

import com.olivamore.api.product.ProductUsage;
import com.olivamore.api.product.ProductUsageCounter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Urun tipini girdi ya da cikti olarak kullanan partiler. */
@Component
class BatchProductUsage implements ProductUsageCounter {

	private final JdbcClient jdbc;

	BatchProductUsage(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<ProductUsage, Long> ofProductType(long typeId) {
		return Map.of(
			ProductUsage.BATCH_INPUT, count("production_batch_input_production_product_types", typeId),
			ProductUsage.BATCH_OUTPUT, count("production_batch_output_production_product_types", typeId));
	}

	private long count(String table, long typeId) {
		return jdbc.sql("select count(*) from " + table + " where production_product_type_id = ?").param(typeId)
			.query(Long.class).single();
	}

}
