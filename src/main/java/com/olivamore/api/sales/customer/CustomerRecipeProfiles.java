package com.olivamore.api.sales.customer;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.olivamore.api.finishedproduct.FinishedProductDirectory;
import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.finishedproduct.RecipeResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	private record ItemRow(Long id, Long orderId, Long finishedProductId, long quantity, LocalDate orderDate) {
	}

	private static final class RecipeHistory {

		private final Long recipeId;
		private final Set<Long> orders = new HashSet<>();
		private long totalQuantity;
		private LocalDate lastOrderDate;
		private long firstItemId = Long.MAX_VALUE;

		private RecipeHistory(Long recipeId) {
			this.recipeId = recipeId;
		}

		private void add(ItemRow row) {
			orders.add(row.orderId());
			totalQuantity += row.quantity();
			firstItemId = Math.min(firstItemId, row.id());
			if (row.orderDate() != null && (lastOrderDate == null || row.orderDate().isAfter(lastOrderDate))) {
				lastOrderDate = row.orderDate();
			}
		}

	}

	private final JdbcClient jdbc;
	private final RecipeResources recipes;
	private final FinishedProductDirectory products;

	CustomerRecipeProfiles(JdbcClient jdbc, RecipeResources recipes, FinishedProductDirectory products) {
		this.jdbc = jdbc;
		this.recipes = recipes;
		this.products = products;
	}

	@Transactional(readOnly = true)
	Profile of(long customerId) {
		return new Profile(preferred(customerId), history(customerId));
	}

	private List<PreferredRecipe> preferred(long customerId) {
		List<PreferredRow> rows = jdbc.sql("""
			select finished_product_recipe_id as id, note from customer_preferred_recipes where customer_id = ?
			""").param(customerId).query(PreferredRow.class).list();
		List<Long> order = recipes.inDisplayOrder(rows.stream().map(PreferredRow::id).toList());
		Map<Long, RecipeResponse> byId = recipes.byIds(order);
		return rows.stream().filter(row -> order.contains(row.id()))
			.sorted(Comparator.comparingInt(row -> order.indexOf(row.id())))
			.map(row -> new PreferredRecipe(byId.get(row.id()), row.note())).toList();
	}

	/** Iptal edilmemis siparislerin kalemleri, urunun tarifine gore toplanir. */
	private List<History> history(long customerId) {
		List<ItemRow> items = jdbc.sql("""
			select i.id, i.order_id, i.finished_product_id, i.quantity, o.order_date
			from order_items i join orders o on o.id = i.order_id
			where o.customer_id = ? and o.status <> 'cancelled'
			""").param(customerId).query(ItemRow.class).list();
		Map<Long, Long> recipeOf = products.recipeIdsOf(items.stream().map(ItemRow::finishedProductId).toList());
		Map<Long, RecipeHistory> groups = new LinkedHashMap<>();
		items.stream().filter(item -> recipeOf.containsKey(item.finishedProductId())).forEach(item -> groups
			.computeIfAbsent(recipeOf.get(item.finishedProductId()), RecipeHistory::new).add(item));
		List<RecipeHistory> rows = groups.values().stream()
			.sorted(Comparator.<RecipeHistory>comparingLong(g -> g.totalQuantity).reversed()
				.thenComparingLong(g -> g.firstItemId))
			.toList();
		Map<Long, RecipeResponse> byId = recipes.byIds(rows.stream().map(g -> g.recipeId).toList());
		return rows.stream()
			.map(g -> new History(byId.get(g.recipeId), g.orders.size(), g.totalQuantity,
				g.lastOrderDate == null ? null : g.lastOrderDate.toString()))
			.toList();
	}

}
