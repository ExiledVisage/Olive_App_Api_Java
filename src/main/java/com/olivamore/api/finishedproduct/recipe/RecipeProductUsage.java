package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.product.ProductUsage;
import com.olivamore.api.product.ProductUsageCounter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Urun tipini kullanan tarif icerikleri. */
@Component
class RecipeProductUsage implements ProductUsageCounter {

	private final JdbcClient jdbc;

	RecipeProductUsage(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<ProductUsage, Long> ofProductType(long typeId) {
		long count = jdbc.sql(
				"select count(*) from finished_product_recipe_contents where production_product_type_id = ?")
			.param(typeId).query(Long.class).single();
		return Map.of(ProductUsage.RECIPE_CONTENT, count);
	}

}
