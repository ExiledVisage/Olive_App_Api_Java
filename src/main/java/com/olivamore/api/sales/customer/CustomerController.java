package com.olivamore.api.sales.customer;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Laravel {@code CustomerController}: musteriler, adresler, formul tercihleri. */
@RestController
class CustomerController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");

	private final Db db;
	private final LaravelValidator validator;
	private final CustomerService customers;
	private final CustomerResource resource;

	CustomerController(Db db, LaravelValidator validator, CustomerService customers, CustomerResource resource) {
		this.db = db;
		this.validator = validator;
		this.customers = customers;
		this.resource = resource;
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
			return new ListResponse.Plain<>(resource.withAddresses(db.select("select * from customers" + where + order, args.toArray())));
		}
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate from customers" + where, args.toArray());
		List<Row> rows = total == 0 ? List.of() : db.select("select * from customers" + where + order
			+ " limit " + perPage.get() + " offset " + (long) (page - 1) * perPage.get(), args.toArray());
		return LaravelPaginator.paginate(resource.withAddresses(rows), total, page, perPage.get(), request);
	}

	@GetMapping("/api/customers/{customer}")
	@RequiresPermission("customer.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
		return DataResponse.of(resource.withAddresses(List.of(customer)).getFirst());
	}

	/** 201; cevaptaki musteri yalnizca verilen alanlarla (Laravel {@code create}), adresler yuklu. */
	@PostMapping("/api/customers")
	@RequiresPermission("customer.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, CustomerRequests.store());
		return DataResponse.created(resource.withAddresses(List.of(customers.create(v.all()))).getFirst());
	}

	@RequestMapping(path = "/api/customers/{customer}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("customer.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, CustomerRequests.update());
		return DataResponse.of(resource.withAddresses(List.of(customers.update(customer, v.all()))).getFirst());
	}

	@DeleteMapping("/api/customers/{customer}")
	@RequiresPermission("customer.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
		customers.delete(customer);
		return Map.of("message", "Müşteri başarıyla silindi.");
	}

	// ---- adres ---------------------------------------------------------------------

	@GetMapping("/api/customers/{customer}/addresses")
	@RequiresPermission("customer.view")
	ListResponse<Map<String, Object>> addresses(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
		return new ListResponse.Plain<>(customers.addresses(customer).stream().map(CustomerAddressResource::of).toList());
	}

	/** 201; cevap yalnizca verilen alanlarla (verilmeyen {@code is_default} null). */
	@PostMapping("/api/customers/{customer}/addresses")
	@RequiresPermission("customer.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeAddress(
		@Bound(value = "customer", table = "customers", model = "Customer") Row customer, RequestInput input) {
		ValidatedInput v = validator.validate(input, CustomerRequests.storeAddress());
		return DataResponse.created(CustomerAddressResource.of(customers.addAddress(customer.id(), v.all())));
	}

	@PatchMapping("/api/customers/{customer}/addresses/{address}")
	@RequiresPermission("customer.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateAddress(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		@Bound(value = "address", table = "customer_addresses", model = "CustomerAddress") Row address, RequestInput input) {
		ValidatedInput v = validator.validate(input, CustomerRequests.updateAddress());
		assertAddressBelongsToCustomer(customer, address);
		return DataResponse.of(CustomerAddressResource.of(customers.updateAddress(customer, address, v.all())));
	}

	@DeleteMapping("/api/customers/{customer}/addresses/{address}")
	@RequiresPermission("customer.manage")
	@Transactional
	Map<String, String> destroyAddress(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		@Bound(value = "address", table = "customer_addresses", model = "CustomerAddress") Row address) {
		assertAddressBelongsToCustomer(customer, address);
		customers.deleteAddress(address);
		return Map.of("message", "Adres başarıyla silindi.");
	}

	// ---- formul tercihleri ------------------------------------------------------------

	@GetMapping("/api/customers/{customer}/recipes")
	@RequiresPermission("customer.view")
	Map<String, Object> recipes(@Bound(value = "customer", table = "customers", model = "Customer") Row customer) {
		return Map.of("data", customers.recipeProfile(customer));
	}

	/** 201 */
	@PostMapping("/api/customers/{customer}/preferred-recipes")
	@RequiresPermission("customer.manage")
	@Transactional
	ResponseEntity<Map<String, String>> storePreferredRecipe(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, CustomerRequests.storePreferredRecipe());
		customers.addPreferredRecipe(customer, Php.intval(v.get("finished_product_recipe_id")), v.get("note"));
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Formül müşterinin tercihlerine eklendi."));
	}

	@DeleteMapping("/api/customers/{customer}/preferred-recipes/{recipe}")
	@RequiresPermission("customer.manage")
	@Transactional
	Map<String, String> destroyPreferredRecipe(@Bound(value = "customer", table = "customers", model = "Customer") Row customer,
		@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		customers.removePreferredRecipe(customer, recipe);
		return Map.of("message", "Formül müşterinin tercihlerinden çıkarıldı.");
	}

	/** Baska musterinin adresi: 404 (dogrulamadan sonra, Laravel'deki gibi). */
	private static void assertAddressBelongsToCustomer(Row customer, Row address) {
		if (Php.intval(address.get("customer_id")) != customer.id()) {
			throw new HttpStatusException(404, "Bu adres bu müşteriye ait değil.");
		}
	}

}
