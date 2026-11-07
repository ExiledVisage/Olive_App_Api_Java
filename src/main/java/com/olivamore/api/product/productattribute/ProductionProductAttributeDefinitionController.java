package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Laravel {@code ProductionProductAttributeDefinitionController}: bir urun kategorisinin attribute
 * eslemeleri. Laravel'de yalnizca giris ister (izin kontrolu yok).
 */
@RestController
class ProductionProductAttributeDefinitionController {

	private final LaravelValidator validator;
	private final ProductionProductAttributeDefinitionService mappings;
	private final ProductionProductAttributeDefinitionResource resource;

	ProductionProductAttributeDefinitionController(LaravelValidator validator, ProductionProductAttributeDefinitionService mappings,
		ProductionProductAttributeDefinitionResource resource) {
		this.validator = validator;
		this.mappings = mappings;
		this.resource = resource;
	}

	@GetMapping("/api/production-products/{productionProduct}/attribute-definitions")
	ListResponse<Map<String, Object>> index(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct) {
		return new ListResponse.Plain<>(resource.mappings(mappings.forProduct(productionProduct), true));
	}

	@GetMapping("/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}")
	DataResponse<Map<String, Object>> show(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct,
		@Bound(table = "production_product_attribute_definitions", model = "ProductionProductAttributeDefinition") Row attributeDefinition) {
		ProductionProductAttributeDefinitionService.ensureBelongs(productionProduct, attributeDefinition);
		return DataResponse.of(one(attributeDefinition));
	}

	/** Cevap 200 ({@code ->fresh()}). */
	@PostMapping("/api/production-products/{productionProduct}/attribute-definitions")
	@Transactional
	DataResponse<Map<String, Object>> store(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionProductAttributeDefinitionRequests.store());
		return DataResponse.of(one(mappings.create(productionProduct, v)));
	}

	@RequestMapping(path = "/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}",
		method = {RequestMethod.PUT, RequestMethod.PATCH})
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct,
		@Bound(table = "production_product_attribute_definitions", model = "ProductionProductAttributeDefinition") Row attributeDefinition,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionProductAttributeDefinitionRequests.update());
		return DataResponse.of(one(mappings.update(productionProduct, attributeDefinition, v)));
	}

	@DeleteMapping("/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}")
	@Transactional
	ResponseEntity<Void> destroy(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct,
		@Bound(table = "production_product_attribute_definitions", model = "ProductionProductAttributeDefinition") Row attributeDefinition) {
		mappings.delete(productionProduct, attributeDefinition);
		return ResponseEntity.noContent().build();
	}

	private Map<String, Object> one(Row mapping) {
		return resource.mappings(List.of(mapping), true).getFirst();
	}

}
