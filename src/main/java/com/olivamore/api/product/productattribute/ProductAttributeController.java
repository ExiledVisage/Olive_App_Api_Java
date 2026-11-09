package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Bir urun kategorisinin attribute eslemeleri: yalnizca giris ister. */
@RestController
@RequestMapping("/api/production-products/{productId}/attribute-definitions")
class ProductAttributeController {

	private final ProductAttributeService service;

	ProductAttributeController(ProductAttributeService service) {
		this.service = service;
	}

	@GetMapping
	ListResponse<ProductAttributeResponse> index(@PathVariable long productId) {
		return new ListResponse.Plain<>(service.forProduct(productId));
	}

	@GetMapping("/{id}")
	DataResponse<ProductAttributeResponse> show(@PathVariable long productId, @PathVariable long id) {
		return DataResponse.of(service.get(productId, id));
	}

	@PostMapping
	DataResponse<ProductAttributeResponse> store(@PathVariable long productId,
		@Valid @RequestBody CreateProductAttributeRequest request) {
		return DataResponse.of(service.create(productId, request));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<ProductAttributeResponse> update(@PathVariable long productId, @PathVariable long id,
		@Valid @RequestBody UpdateProductAttributeRequest request) {
		return DataResponse.of(service.update(productId, id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> destroy(@PathVariable long productId, @PathVariable long id) {
		service.delete(productId, id);
		return ResponseEntity.noContent().build();
	}

}
