package com.olivamore.api.product.productiontype;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.productionline.ProductionLineResponse;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionProductTypeController} (production_product_type.view / .manage). */
@RestController
class ProductionProductTypeController {

	private final LaravelValidator validator;
	private final ProductionProductTypeService types;
	private final ProductionProductTypeResource resource;
	private final ProductionLineResources lines;

	ProductionProductTypeController(LaravelValidator validator, ProductionProductTypeService types,
		ProductionProductTypeResource resource, ProductionLineResources lines) {
		this.validator = validator;
		this.types = types;
		this.resource = resource;
		this.lines = lines;
	}

	@GetMapping("/api/production-product-types")
	@RequiresPermission("production_product_type.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(resource.productTypes(types.all()));
	}

	@GetMapping("/api/production-product-types/{productionProductType}")
	@RequiresPermission("production_product_type.view")
	DataResponse<Map<String, Object>> show(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		return DataResponse.of(resource.productType(productionProductType));
	}

	/** Tipin kategorisini (tip ozelinde ya da tip belirtmeden) girdi ya da cikti olarak kullanan hatlar. */
	@GetMapping("/api/production-product-types/{productionProductType}/production-lines")
	@RequiresPermission("production_product_type.view")
	ListResponse<ProductionLineResponse> productionLines(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		return new ListResponse.Plain<>(lines.usingProductType(
			productionProductType.longValue("production_product_id"), productionProductType.id()));
	}

	@GetMapping("/api/production-product-types/{productionProductType}/flow")
	@RequiresPermission("production_product_type.view")
	DataResponse<Map<String, Object>> flow(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		long productId = productionProductType.longValue("production_product_id");
		List<ProductionLineResponse> inputs = lines.withInputType(productId, productionProductType.id());
		List<ProductionLineResponse> outputs = lines.withOutputType(productId, productionProductType.id());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("production_product_type", resource.productType(productionProductType));
		data.put("input_production_lines", inputs);
		data.put("output_production_lines", outputs);
		data.put("production_lines", ProductionLineResources.merge(inputs, outputs));
		return DataResponse.of(data);
	}

	@PostMapping("/api/production-product-types")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionProductTypeRequests.store());
		return DataResponse.created(resource.productType(types.create(v.all())));
	}

	@RequestMapping(path = "/api/production-product-types/{productionProductType}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_product_type.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionProductTypeRequests.update(productionProductType.id()));
		return DataResponse.of(resource.productType(types.update(productionProductType, v.all())));
	}

	@DeleteMapping("/api/production-product-types/{productionProductType}")
	@RequiresPermission("production_product_type.manage")
	@Transactional
	Map<String, String> destroy(
		@Bound(table = "production_product_types", model = "ProductionProductType") Row productionProductType) {
		types.delete(productionProductType);
		return Map.of("message", "ProductionProductType deleted successfully.");
	}

}
