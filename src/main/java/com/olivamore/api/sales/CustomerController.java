package com.olivamore.api.sales;

import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.InputNormalizationFilter;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Laravel {@code CustomerController} + {@code CustomerService}: musteriler, adresler, formul tercihleri. */
@RestController
class CustomerController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");
	private static final String[] ADDRESS_FIELDS = {"label", "country", "city", "district", "neighborhood", "street", "building_no",
		"apartment_no", "postal_code", "full_address", "latitude", "longitude", "is_default"};

	private final Db db;
	private final LaravelValidator validator;
	private final RecipeResources recipes;

	CustomerController(Db db, LaravelValidator validator, RecipeResources recipes) {
		this.db = db;
		this.validator = validator;
		this.recipes = recipes;
	}

	// ---- musteri -----------------------------------------------------------------

	/** {@code ?is_active}; en yeni once; {@code per_page} ile sayfali; adresler yuklu. */
	@GetMapping("/api/customers")
	@RequiresPermission("customer.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		String where = "";
		List<Object> args = new ArrayList<>();
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			String value = request.getParameter("is_active");
			where = " where is_active = ?";
			args.add(value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT)));
		}
		String order = " order by created_at desc";
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(customers(db.select("select * from customers" + where + order, args.toArray())));
		}
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate from customers" + where, args.toArray());
		List<Row> rows = total == 0 ? List.of() : db.select("select * from customers" + where + order
			+ " limit " + perPage.get() + " offset " + (long) (page - 1) * perPage.get(), args.toArray());
		return LaravelPaginator.paginate(customers(rows), total, page, perPage.get(), request);
	}

	@GetMapping("/api/customers/{customer}")
	@RequiresPermission("customer.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
		return DataResponse.of(customers(List.of(customer)).getFirst());
	}

	/** 201; cevaptaki musteri yalnizca verilen alanlarla (Laravel {@code create}), adresler yuklu. */
	@PostMapping("/api/customers")
	@RequiresPermission("customer.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		Rules rules = Rules.of()
			.field("name", "required|string|max:255")
			.field("company_name", "nullable|string|max:255")
			.field("phone", "nullable|string|max:30")
			.field("email", "nullable|email|max:255")
			.field("notes", "nullable|string")
			.field("is_active", "nullable|boolean")
			.field("addresses", "nullable|array");
		addressRules(rules, "addresses.*.", "required");
		ValidatedInput v = validator.validate(input, rules);
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		Object addresses = data.remove("addresses");
		if (data.get("is_active") == null) {
			data.put("is_active", true);
		}
		Row customer = db.create("customers", data);
		for (Map<String, Object> address : items(addresses)) {
			addAddress(customer.id(), address);
		}
		return DataResponse.created(customers(List.of(customer)).getFirst());
	}

	@RequestMapping(path = "/api/customers/{customer}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("customer.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|required|string|max:255")
			.field("company_name", "nullable|string|max:255")
			.field("phone", "nullable|string|max:30")
			.field("email", "nullable|email|max:255")
			.field("notes", "nullable|string")
			.field("is_active", "nullable|boolean"));
		// Laravel NOT NULL kolona null yazip 500 veriyordu (DEVIATIONS 16): null "degistirme" demek
		db.update("customers", customer.id(), withoutNull(v.all(), "is_active"));
		return DataResponse.of(customers(List.of(db.find("customers", customer.id()).orElseThrow())).getFirst());
	}

	@DeleteMapping("/api/customers/{customer}")
	@RequiresPermission("customer.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
		if (db.count("select count(*) from orders where customer_id = ?", customer.id()) > 0) {
			throw new BusinessRuleException("Bu müşteriye ait siparişler bulunduğu için silinemez.");
		}
		db.delete("customers", customer.id());
		return Map.of("message", "Müşteri başarıyla silindi.");
	}

	// ---- adres ---------------------------------------------------------------------

	/** En yeni once. */
	@GetMapping("/api/customers/{customer}/addresses")
	@RequiresPermission("customer.view")
	ListResponse<Map<String, Object>> addresses(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
		return new ListResponse.Plain<>(db.select("select * from customer_addresses where customer_id = ? and customer_id is not null"
			+ " order by created_at desc", customer.id()).stream().map(CustomerController::address).toList());
	}

	/** 201; cevap yalnizca verilen alanlarla (verilmeyen {@code is_default} null). */
	@PostMapping("/api/customers/{customer}/addresses")
	@RequiresPermission("customer.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeAddress(
		@Bound(value = "customer", table = "customers", model = "Customer") Row customer, RequestInput input) {
		ValidatedInput v = validator.validate(input, addressRules(Rules.of(), "", "required"));
		return DataResponse.created(address(addAddress(customer.id(), v.all())));
	}

	@PatchMapping("/api/customers/{customer}/addresses/{address}")
	@RequiresPermission("customer.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateAddress(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		@Bound(value = "address", table = "customer_addresses", model = "CustomerAddress") Row address, RequestInput input) {
		ValidatedInput v = validator.validate(input, addressRules(Rules.of(), "", "sometimes|required"));
		assertAddressBelongsToCustomer(customer, address);
		if (!empty(v.get("is_default"))) {
			db.execute("update customer_addresses set is_default = ?, updated_at = ? where customer_id = ? and customer_id is not null"
				+ " and id != ?", false, Timestamps.now(), customer.id(), address.id());
		}
		db.update("customer_addresses", address.id(), withoutNull(v.all(), "is_default", "country"));
		return DataResponse.of(address(db.find("customer_addresses", address.id()).orElseThrow()));
	}

	/** Siparise bagli adres silinmez; varsayilan adres silinirse en son eklenen varsayilan olur. */
	@DeleteMapping("/api/customers/{customer}/addresses/{address}")
	@RequiresPermission("customer.manage")
	@Transactional
	Map<String, String> destroyAddress(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		@Bound(value = "address", table = "customer_addresses", model = "CustomerAddress") Row address) {
		assertAddressBelongsToCustomer(customer, address);
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
		return Map.of("message", "Adres başarıyla silindi.");
	}

	// ---- formul tercihleri ------------------------------------------------------------

	/**
	 * Formul profili: elle atanmis tercihler (ada, sonra yeni versiyona gore) ve iptal edilmemis siparislerden
	 * turetilen gecmis (toplam adede gore).
	 */
	@GetMapping("/api/customers/{customer}/recipes")
	@RequiresPermission("customer.view")
	Map<String, Object> recipes(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
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
		return Map.of("data", data);
	}

	/** Ayni formul tekrar eklenirse yalnizca notu guncellenir (201). */
	@PostMapping("/api/customers/{customer}/preferred-recipes")
	@RequiresPermission("customer.manage")
	@Transactional
	ResponseEntity<Map<String, String>> storePreferredRecipe(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("finished_product_recipe_id", "required|integer|exists:finished_product_recipes,id")
			.field("note", "nullable|string|max:255"));
		long recipeId = Php.intval(v.get("finished_product_recipe_id"));
		LocalDateTime now = Timestamps.now();
		if (db.count("select count(*) from customer_preferred_recipes where customer_id = ? and finished_product_recipe_id = ?",
			customer.id(), recipeId) > 0) {
			db.execute("update customer_preferred_recipes set note = ?, updated_at = ? where customer_id = ? and finished_product_recipe_id = ?",
				v.get("note"), now, customer.id(), recipeId);
		}
		else {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("customer_id", customer.id());
			values.put("finished_product_recipe_id", recipeId);
			values.put("note", v.get("note"));
			db.insert("customer_preferred_recipes", values);
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Formül müşterinin tercihlerine eklendi."));
	}

	@DeleteMapping("/api/customers/{customer}/preferred-recipes/{recipe}")
	@RequiresPermission("customer.manage")
	@Transactional
	Map<String, String> destroyPreferredRecipe(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		int detached = db.execute("delete from customer_preferred_recipes where customer_id = ? and finished_product_recipe_id = ?",
			customer.id(), recipe.id());
		if (detached == 0) {
			throw new BusinessRuleException("'" + recipe.string("name") + "' bu müşterinin tercihlerinde değil.");
		}
		return Map.of("message", "Formül müşterinin tercihlerinden çıkarıldı.");
	}

	// ---- yardimcilar ---------------------------------------------------------------------

	private static Rules addressRules(Rules rules, String prefix, String presence) {
		return rules
			.field(prefix + "label", presence + "|string|max:100")
			.field(prefix + "country", "nullable|string|max:10")
			.field(prefix + "city", presence + "|string|max:100")
			.field(prefix + "district", "nullable|string|max:100")
			.field(prefix + "neighborhood", "nullable|string|max:150")
			.field(prefix + "street", "nullable|string|max:150")
			.field(prefix + "building_no", "nullable|string|max:20")
			.field(prefix + "apartment_no", "nullable|string|max:20")
			.field(prefix + "postal_code", "nullable|string|max:10")
			.field(prefix + "full_address", "nullable|string")
			.field(prefix + "latitude", "nullable|numeric|between:-90,90")
			.field(prefix + "longitude", "nullable|numeric|between:-180,180")
			.field(prefix + "is_default", "nullable|boolean");
	}

	/** {@code addAddress}: varsayilan verilirse digerleri varsayilan olmaktan cikar. @return olusan model (verilen alanlar) */
	private Row addAddress(long customerId, Map<String, Object> data) {
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

	private static void assertAddressBelongsToCustomer(Row customer, Row address) {
		if (Php.intval(address.get("customer_id")) != customer.id()) {
			throw new HttpStatusException(404, "Bu adres bu müşteriye ait değil.");
		}
	}

	/** CustomerResource; adresler yuklu (Laravel eager loading sirasiyla). */
	private List<Map<String, Object>> customers(List<Row> customers) {
		Map<Long, List<Row>> addresses = Relations.groupBy(
			db.whereIn("customer_addresses", "customer_id", Relations.ids(customers), null), "customer_id");
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row c : customers) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", c.get("id"));
			r.put("name", c.get("name"));
			r.put("company_name", c.get("company_name"));
			r.put("phone", c.get("phone"));
			r.put("email", c.get("email"));
			r.put("notes", c.get("notes"));
			r.put("is_active", Casts.bool(c.get("is_active")));
			r.put("addresses", addresses.getOrDefault(c.id(), List.of()).stream().map(CustomerController::address).toList());
			r.put("created_at", c.get("created_at"));
			r.put("updated_at", c.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** CustomerAddressResource ({@code latitude}/{@code longitude}: float, {@code is_default}: boolean). */
	static Map<String, Object> address(Row a) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", a.get("id"));
		r.put("customer_id", a.get("customer_id"));
		for (String field : ADDRESS_FIELDS) {
			Object value = a.get(field);
			r.put(field, switch (field) {
				case "latitude", "longitude" -> value == null ? null : Php.floatval(value);
				case "is_default" -> Casts.bool(value);
				default -> value;
			});
		}
		r.put("created_at", a.get("created_at"));
		r.put("updated_at", a.get("updated_at"));
		return r;
	}

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
