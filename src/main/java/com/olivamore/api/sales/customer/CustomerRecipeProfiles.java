package com.olivamore.api.sales.customer;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.finishedproduct.RecipeResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Musterinin elle atanmis formul tercihleri ve iptal edilmemis siparislerden turetilen gecmisi. */
@Component
class CustomerRecipeProfiles {

	record Profile(List<PreferredRecipe> preferred, List<History> history) {
	}

	/** Tarif alanlari ve elle yazilan not ayni seviyede. */
	record PreferredRecipe(@JsonUnwrapped RecipeResponse recipe, String note) {
	}

	record History(RecipeResponse recipe, long orderCount, long totalQuantity, String lastOrderDate) {
	}

	private record PreferredRow(Long id, String note) {
	}

	private record HistoryRow(Long recipeId, long orderCount, long totalQuantity, LocalDate lastOrderDate) {
	}

	private final JdbcClient jdbc;
	private final RecipeResources recipes;

	CustomerRecipeProfiles(JdbcClient jdbc, RecipeResources recipes) {
		this.jdbc = jdbc;
		this.recipes = recipes;
	}

	@Transactional(readOnly = true)
	Profile of(long customerId) {
		return new Profile(preferred(customerId), history(customerId));
	}

	private List<PreferredRecipe> preferred(long customerId) {
		List<PreferredRow> rows = jdbc.sql("""
			select r.id, p.note
			from customer_preferred_recipes p
			join finished_product_recipes r on r.id = p.finished_product_recipe_id
			where p.customer_id = ?
			order by r.name, r.version desc
			""").param(customerId).query(PreferredRow.class).list();
		Map<Long, RecipeResponse> byId = recipes.byIds(rows.stream().map(PreferredRow::id).toList());
		return rows.stream().map(row -> new PreferredRecipe(byId.get(row.id()), row.note())).toList();
	}

	private List<History> history(long customerId) {
		List<HistoryRow> rows = jdbc.sql("""
			select p.finished_product_recipe_id as recipe_id, count(distinct i.order_id) as order_count,
				sum(i.quantity) as total_quantity, max(o.order_date) as last_order_date
			from order_items i
			join orders o on o.id = i.order_id
			join finished_products p on p.id = i.finished_product_id
			where o.customer_id = ? and o.status <> 'cancelled' and p.finished_product_recipe_id is not null
			group by p.finished_product_recipe_id
			order by total_quantity desc, min(i.id)
			""").param(customerId).query(HistoryRow.class).list();
		Map<Long, RecipeResponse> byId = recipes.byIds(rows.stream().map(HistoryRow::recipeId).toList());
		return rows.stream()
			.map(row -> new History(byId.get(row.recipeId()), row.orderCount(), row.totalQuantity(),
				row.lastOrderDate() == null ? null : row.lastOrderDate().toString()))
			.toList();
	}

}
