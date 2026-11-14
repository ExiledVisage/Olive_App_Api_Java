package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Bir urun kategorisinin attribute eslemeleri; kategori izinleriyle (okuma view, yazma manage). */
@RestController
@RequestMapping("/api/production-products/{productId}/attribute-definitions")
class ProductAttributeController {

	private final ProductAttributeService service;

	ProductAttributeController(ProductAttributeService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('production_product_type.view')")
	ListResponse<ProductAttributeResponse> index(@PathVariable long productId) {
		return new ListResponse<>(service.forProduct(productId));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('production_product_type.view')")
	DataResponse<ProductAttributeResponse> show(@PathVariable long productId, @PathVariable long id) {
		return DataResponse.of(service.get(productId, id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('production_product_type.manage')")
	DataResponse<ProductAttributeResponse> store(@PathVariable long productId,
		@Valid @RequestBody CreateProductAttributeRequest request) {
		return DataResponse.of(service.create(productId, request));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@PreAuthorize("hasAuthority('production_product_type.manage')")
	DataResponse<ProductAttributeResponse> update(@PathVariable long productId, @PathVariable long id,
		@Valid @RequestBody UpdateProductAttributeRequest request) {
		return DataResponse.of(service.update(productId, id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('production_product_type.manage')")
	ResponseEntity<Void> destroy(@PathVariable long productId, @PathVariable long id) {
		service.delete(productId, id);
		return ResponseEntity.noContent().build();
	}

}
