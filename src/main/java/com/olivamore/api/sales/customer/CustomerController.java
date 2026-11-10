package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/** Musteriler, adresler, formul tercihleri. */
@RestController
@RequestMapping("/api/customers")
class CustomerController {

	private final CustomerService service;
	private final CustomerRecipeProfiles profiles;

	CustomerController(CustomerService service, CustomerRecipeProfiles profiles) {
		this.service = service;
		this.profiles = profiles;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('customer.view')")
	ListResponse<CustomerResponse> index(@RequestParam(name = "is_active", required = false) Boolean isActive,
		HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(service.all(isActive));
		}
		int page = Listing.currentPage(request);
		Page<CustomerResponse> rows = service.page(isActive, page, perPage.get());
		return LaravelPaginator.paginate(rows.getContent(), rows.getTotalElements(), page, perPage.get(), request);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('customer.view')")
	DataResponse<CustomerResponse> show(@PathVariable long id) {
		return DataResponse.of(service.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('customer.manage')")
	ResponseEntity<DataResponse<CustomerResponse>> store(@Valid @RequestBody CreateCustomerRequest request) {
		return DataResponse.created(service.get(service.create(request)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@PreAuthorize("hasAuthority('customer.manage')")
	DataResponse<CustomerResponse> update(@PathVariable long id, @Valid @RequestBody UpdateCustomerRequest request) {
		service.update(id, request);
		return DataResponse.of(service.get(id));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('customer.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Müşteri başarıyla silindi.");
	}

	@GetMapping("/{id}/addresses")
	@PreAuthorize("hasAuthority('customer.view')")
	ListResponse<CustomerAddressResponse> addresses(@PathVariable long id) {
		return new ListResponse.Plain<>(service.addresses(id));
	}

	@PostMapping("/{id}/addresses")
	@PreAuthorize("hasAuthority('customer.manage')")
	ResponseEntity<DataResponse<CustomerAddressResponse>> storeAddress(@PathVariable long id,
		@Valid @RequestBody CreateCustomerAddressRequest request) {
		return DataResponse.created(service.addAddress(id, request));
	}

	@PatchMapping("/{id}/addresses/{addressId}")
	@PreAuthorize("hasAuthority('customer.manage')")
	DataResponse<CustomerAddressResponse> updateAddress(@PathVariable long id, @PathVariable long addressId,
		@Valid @RequestBody UpdateCustomerAddressRequest request) {
		return DataResponse.of(service.updateAddress(id, addressId, request));
	}

	@DeleteMapping("/{id}/addresses/{addressId}")
	@PreAuthorize("hasAuthority('customer.manage')")
	Map<String, String> destroyAddress(@PathVariable long id, @PathVariable long addressId) {
		service.deleteAddress(id, addressId);
		return Map.of("message", "Adres başarıyla silindi.");
	}

	@GetMapping("/{id}/recipes")
	@PreAuthorize("hasAuthority('customer.view')")
	DataResponse<CustomerRecipeProfiles.Profile> recipes(@PathVariable long id) {
		service.ensureExists(id);
		return DataResponse.of(profiles.of(id));
	}

	@PostMapping("/{id}/preferred-recipes")
	@PreAuthorize("hasAuthority('customer.manage')")
	ResponseEntity<Map<String, String>> storePreferredRecipe(@PathVariable long id,
		@Valid @RequestBody PreferredRecipeRequest request) {
		service.addPreferredRecipe(id, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(Map.of("message", "Formül müşterinin tercihlerine eklendi."));
	}

	@DeleteMapping("/{id}/preferred-recipes/{recipeId}")
	@PreAuthorize("hasAuthority('customer.manage')")
	Map<String, String> destroyPreferredRecipe(@PathVariable long id, @PathVariable long recipeId) {
		service.removePreferredRecipe(id, recipeId);
		return Map.of("message", "Formül müşterinin tercihlerinden çıkarıldı.");
	}

}
