package com.olivamore.api.sales.customer;

import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Laravel {@code CustomerService}: musteriler, adresler, formul tercihleri ve profili. */
@Service
class CustomerService {

	private final Db db;
	private final RecipeResources recipes;

	CustomerService(Db db, RecipeResources recipes) {
		this.db = db;
		this.recipes = recipes;
	}

	// ---- musteri -----------------------------------------------------------------

	/** {@code is_active} verilmezse true; adresler sirayla eklenir. @return olusan model (yalnizca verilen alanlar) */
	Row create(Map<String, Object> validated) {
		Map<String, Object> data = new LinkedHashMap<>(validated);
		Object addresses = data.remove("addresses");
		if (data.get("is_active") == null) {
			data.put("is_active", true);
		}
		Row customer = db.create("customers", data);
		for (Map<String, Object> address : items(addresses)) {
			addAddress(customer.id(), address);
		}
		return customer;
	}

	/** Laravel NOT NULL kolona null yazip 500 veriyordu (DEVIATIONS 16): null "degistirme" demek. */
	Row update(Row customer, Map<String, Object> validated) {
		db.update("customers", customer.id(), withoutNull(validated, "is_active"));
		return db.find("customers", customer.id()).orElseThrow();
	}

	void delete(Row customer) {
		if (db.count("select count(*) from orders where customer_id = ?", customer.id()) > 0) {
			throw new BusinessRuleException("Bu müşteriye ait siparişler bulunduğu için silinemez.");
		}
		db.delete("customers", customer.id());
	}

	// ---- adres ---------------------------------------------------------------------

	/** En yeni once. */
	List<Row> addresses(Row customer) {
		return db.select("select * from customer_addresses where customer_id = ? and customer_id is not null order by created_at desc",
			customer.id());
	}

	/** Varsayilan verilirse digerleri varsayilan olmaktan cikar; ulke verilmezse TR. @return olusan model (verilen alanlar) */
	Row addAddress(long customerId, Map<String, Object> data) {
		if (!empty(data.get("is_default"))) {
			db.execute("update customer_addresses set is_default = ?, updated_at = ? where customer_id = ? and customer_id is not null",
				false, Timestamps.now(), customerId);
		}
		Map<String, Object> values = withoutNull(data, "is_default");
		values.put("customer_id", customerId);
		if (values.get("country") == null) {
			values.put("country", "TR");
		}
		return db.create("customer_addresses", values);
	}

	Row updateAddress(Row customer, Row address, Map<String, Object> validated) {
		if (!empty(validated.get("is_default"))) {
			db.execute("update customer_addresses set is_default = ?, updated_at = ? where customer_id = ? and customer_id is not null"
				+ " and id != ?", false, Timestamps.now(), customer.id(), address.id());
		}
		db.update("customer_addresses", address.id(), withoutNull(validated, "is_default", "country"));
		return db.find("customer_addresses", address.id()).orElseThrow();
	}

	/** Siparise bagli adres silinmez; varsayilan adres silinirse en son eklenen varsayilan olur. */
	void deleteAddress(Row address) {
		DeletionGuard.of("'" + Php.strval(address.get("label")) + "' adresi")
			.usedBy("sipariş", db.count("select count(*) from orders where delivery_address_id = ?", address.id()))
			.check();
		boolean wasDefault = Boolean.TRUE.equals(Casts.bool(address.get("is_default")));
		db.delete("customer_addresses", address.id());
		if (wasDefault) {
			db.first("select id from customer_addresses where customer_id = ? order by id desc limit 1", address.get("customer_id"))
				.ifPresent(latest -> db.execute("update customer_addresses set is_default = ?, updated_at = ? where id = ?",
					true, Timestamps.now(), latest.id()));
		}
	}

	// ---- formul tercihleri ------------------------------------------------------------

	/**
	 * {@code getRecipeProfile}: elle atanmis tercihler (ada, sonra yeni versiyona gore) ve iptal edilmemis
	 * siparislerden turetilen gecmis (toplam adede gore).
	 */
	Map<String, Object> recipeProfile(Row customer) {
		List<Row> preferredRows = db.select("select finished_product_recipes.*, customer_preferred_recipes.customer_id as pivot_customer_id,"
			+ " customer_preferred_recipes.finished_product_recipe_id as pivot_finished_product_recipe_id,"
			+ " customer_preferred_recipes.note as pivot_note from finished_product_recipes inner join customer_preferred_recipes"
			+ " on finished_product_recipes.id = customer_preferred_recipes.finished_product_recipe_id"
			+ " where customer_preferred_recipes.customer_id = ? order by name asc, version desc", customer.id());
		List<Map<String, Object>> preferredResources = recipes.recipes(preferredRows, false);
		List<Map<String, Object>> preferred = new ArrayList<>();
		for (int i = 0; i < preferredRows.size(); i++) {
			Map<String, Object> r = new LinkedHashMap<>(preferredResources.get(i));
			r.put("note", preferredRows.get(i).get("pivot_note"));
			preferred.add(r);
		}

		List<Row> items = db.select("select * from order_items where exists (select * from orders where order_items.order_id = orders.id"
			+ " and customer_id = ? and status != ?)", customer.id(), "cancelled");
		Map<Long, Row> orders = Relations.byId(db.whereIn("orders", "id", Relations.column(items, "order_id"), null));
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(items, "finished_product_id"), null));
		Map<Long, Row> recipeRows = Relations.byId(db.whereIn("finished_product_recipes", "id",
			Relations.column(List.copyOf(products.values()), "finished_product_recipe_id"), null));
		Map<Long, List<Row>> groups = new LinkedHashMap<>();
		for (Row item : items) {
			Row product = item.longValue("finished_product_id") == null ? null : products.get(item.longValue("finished_product_id"));
			Row recipe = product == null || product.longValue("finished_product_recipe_id") == null ? null
				: recipeRows.get(product.longValue("finished_product_recipe_id"));
			if (recipe != null) {
				groups.computeIfAbsent(recipe.id(), k -> new ArrayList<>()).add(item);
			}
		}
		Map<Long, Map<String, Object>> recipeResources = new LinkedHashMap<>();
		List<Row> groupRecipes = groups.keySet().stream().map(recipeRows::get).toList();
		List<Map<String, Object>> resolved = recipes.recipes(groupRecipes, false);
		for (int i = 0; i < groupRecipes.size(); i++) {
			recipeResources.put(groupRecipes.get(i).id(), resolved.get(i));
		}
		List<Map<String, Object>> history = new ArrayList<>();
		groups.forEach((recipeId, group) -> {
			Set<Object> orderIds = new LinkedHashSet<>();
			long total = 0;
			LocalDate last = null;
			for (Row item : group) {
				orderIds.add(item.get("order_id"));
				total += Php.intval(item.get("quantity"));
				Row order = item.longValue("order_id") == null ? null : orders.get(item.longValue("order_id"));
				LocalDate date = order == null ? null : date(order.get("order_date"));
				if (date != null && (last == null || date.isAfter(last))) {
					last = date;
				}
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("recipe", recipeResources.get(recipeId));
			row.put("order_count", orderIds.size());
			row.put("total_quantity", total);
			row.put("last_order_date", last == null ? null : last.toString());
			history.add(row);
		});
		// sortByDesc: kararli
		history.sort((a, b) -> Long.compare((Long) b.get("total_quantity"), (Long) a.get("total_quantity")));
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("preferred", preferred);
		data.put("history", history);
		return data;
	}

	/** Ayni formul tekrar eklenirse yalnizca notu guncellenir. */
	void addPreferredRecipe(Row customer, long recipeId, Object note) {
		LocalDateTime now = Timestamps.now();
		if (db.count("select count(*) from customer_preferred_recipes where customer_id = ? and finished_product_recipe_id = ?",
			customer.id(), recipeId) > 0) {
			db.execute("update customer_preferred_recipes set note = ?, updated_at = ? where customer_id = ? and finished_product_recipe_id = ?",
				note, now, customer.id(), recipeId);
			return;
		}
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("customer_id", customer.id());
		values.put("finished_product_recipe_id", recipeId);
		values.put("note", note);
		db.insert("customer_preferred_recipes", values);
	}

	void removePreferredRecipe(Row customer, Row recipe) {
		int detached = db.execute("delete from customer_preferred_recipes where customer_id = ? and finished_product_recipe_id = ?",
			customer.id(), recipe.id());
		if (detached == 0) {
			throw new BusinessRuleException("'" + recipe.string("name") + "' bu müşterinin tercihlerinde değil.");
		}
	}

	// ---- yardimcilar ---------------------------------------------------------------------

	/** NOT NULL kolonlara gelen null: Laravel'de 500, burada alan yokmus gibi (DEVIATIONS 16). */
	private static Map<String, Object> withoutNull(Map<String, Object> data, String... columns) {
		Map<String, Object> out = new LinkedHashMap<>(data);
		for (String column : columns) {
			if (out.containsKey(column) && out.get(column) == null) {
				out.remove(column);
			}
		}
		return out;
	}

	/** PHP {@code empty()} */
	private static boolean empty(Object value) {
		return value == null || Boolean.FALSE.equals(value) || "".equals(value) || "0".equals(value)
			|| value instanceof Number n && n.doubleValue() == 0;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>((Collection<Map<String, Object>>) m.values());
		}
		return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

	private static LocalDate date(Object value) {
		return switch (value) {
			case null -> null;
			case LocalDate d -> d;
			case LocalDateTime t -> t.toLocalDate();
			case java.sql.Date d -> d.toLocalDate();
			default -> LocalDate.parse(Php.strval(value).substring(0, 10));
		};
	}

}
