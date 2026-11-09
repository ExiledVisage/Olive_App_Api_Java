package com.olivamore.api.product.productionproduct;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.product.productattribute.ProductAttributeService;
import com.olivamore.api.product.productiontype.ProductionProductTypeResponses;
import com.olivamore.api.productionline.ProductionLineResources;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production-products")
class ProductionProductController {

	private final ProductionProductService service;
	private final ProductAttributeService attributes;
	private final ProductionProductTypeResponses types;
	private final ProductionLineResources lines;

	ProductionProductController(ProductionProductService service, ProductAttributeService attributes,
		ProductionProductTypeResponses types, ProductionLineResources lines) {
		this.service = service;
		this.attributes = attributes;
		this.types = types;
		this.lines = lines;
	}

	@GetMapping
	@RequiresPermission("production_product_type.view")
	ListResponse<ProductionProductResponse> index() {
		Map<Long, Long> counts = service.typeCounts();
		return new ListResponse.Plain<>(service.all().stream()
			.map(product -> ProductionProductResponse.full(product, counts.getOrDefault(product.getId(), 0L), null,
				null, List.of()))
			.toList());
	}

	@GetMapping("/{id}")
	@RequiresPermission("production_product_type.view")
	DataResponse<ProductionProductResponse> show(@PathVariable long id) {
		ProductionProduct product = service.get(id);
		return DataResponse.of(ProductionProductResponse.full(product, service.typeCount(id),
			attributes.summaries(id), types.ofProduct(id), lines.ofProduct(id)));
	}

	@PostMapping
	@RequiresPermission("production_product_type.manage")
	ResponseEntity<DataResponse<ProductionProductResponse>> store(
		@Valid @RequestBody CreateProductionProductRequest request) {
		return DataResponse.created(ProductionProductResponse.basic(service.create(request)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_product_type.manage")
	DataResponse<ProductionProductResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateProductionProductRequest request) {
		return DataResponse.of(ProductionProductResponse.basic(service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	@RequiresPermission("production_product_type.manage")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "ProductionProductType type deleted successfully.");
	}

}
