package com.olivamore.api.packaging.supplier;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Urun (ambalaj) tedarikcileri; zeytin tedarikcisi ayri modulde. */
@RestController
@RequestMapping("/api/product-suppliers")
class ProductSupplierController {

	private final ProductSupplierService service;

	ProductSupplierController(ProductSupplierService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('product_supplier.view')")
	ListResponse<ProductSupplierResponse> index(@RequestParam(name = "is_active", required = false) Boolean isActive,
		@RequestParam(required = false) String search) {
		return new ListResponse<>(service.all(isActive, search));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('product_supplier.view')")
	DataResponse<ProductSupplierResponse> show(@PathVariable long id) {
		return DataResponse.of(service.detail(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('product_supplier.manage')")
	ResponseEntity<DataResponse<ProductSupplierResponse>> store(
		@Valid @RequestBody CreateProductSupplierRequest request) {
		return DataResponse.created(service.detail(service.create(request)));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('product_supplier.manage')")
	DataResponse<ProductSupplierResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateProductSupplierRequest request) {
		service.update(id, request);
		return DataResponse.of(service.detail(id));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('product_supplier.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Ürün tedarikçisi silindi.");
	}

}
