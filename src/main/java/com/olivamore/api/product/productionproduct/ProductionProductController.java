package com.olivamore.api.product.productionproduct;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.product.productattribute.ProductionProductAttributeDefinitionResource;
import com.olivamore.api.product.productiontype.ProductionProductTypeResource;
import com.olivamore.api.productionline.ProductionLineResources;
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
 * Laravel {@code ProductionProductController}: urun kategorileri (production_product_type.view / .manage).
 * Cevap kodlari Laravel'deki gibi: {@code create()} sonucu 201, {@code ->fresh()} 200.
 */
@RestController
class ProductionProductController {

	private final LaravelValidator validator;
	private final ProductionProductService products;
	private final ProductionProductAttributeDefinitionResource mappingResource;
	private final ProductionProductTypeResource typeResource;
	private final ProductionLineResources lines;

	ProductionProductController(LaravelValidator validator, ProductionProductService products,
		ProductionProductAttributeDefinitionResource mappingResource, ProductionProductTypeResource typeResource,
		ProductionLineResources lines) {
		this.validator = validator;
		this.products = products;
		this.mappingResource = mappingResource;
		this.typeResource = typeResource;
		this.lines = lines;
	}

	@GetMapping("/api/production-products")
	@RequiresPermission("production_product_type.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(products.all().stream()
			.map(p -> ProductionProductResource.of(p, p.longValue("production_product_types_count"), null, null, List.of())).toList());
	}

	@GetMapping("/api/production-products/{productionProduct}")
	@RequiresPermission("production_product_type.view")
	DataResponse<Map<String, Object>> show(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct) {
		long id = productionProduct.id();
		List<Map<String, Object>> mappings = mappingResource.ofProduct(id);
		List<Map<String, Object>> types = typeResource.productTypes(products.types(id));
		return DataResponse.of(ProductionProductResource.of(productionProduct, products.typeCount(id), mappings, types,
			lines.ofProduct(id)));
	}

	@PostMapping("/api/production-products")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionProductRequests.store());
		return DataResponse.created(ProductionProductResource.of(products.create(v.all())));
	}

	@RequestMapping(path = "/api/production-products/{productionProduct}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_product_type.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionProductRequests.update(productionProduct.id()));
		return DataResponse.of(ProductionProductResource.of(products.update(productionProduct, v.all())));
	}

	@DeleteMapping("/api/production-products/{productionProduct}")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	Map<String, String> destroy(@Bound(table = "production_products", model = "ProductionProduct") Row productionProduct) {
		products.delete(productionProduct);
		return Map.of("message", "ProductionProductType type deleted successfully.");
	}

}
