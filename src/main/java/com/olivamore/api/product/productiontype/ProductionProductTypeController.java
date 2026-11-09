package com.olivamore.api.product.productiontype;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.productionline.ProductionLineResponse;
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
@RequestMapping("/api/production-product-types")
class ProductionProductTypeController {

	private final ProductionProductTypeService service;
	private final ProductionLineResources lines;

	ProductionProductTypeController(ProductionProductTypeService service, ProductionLineResources lines) {
		this.service = service;
		this.lines = lines;
	}

	@GetMapping
	@RequiresPermission("production_product_type.view")
	ListResponse<ProductionProductTypeResponse> index() {
		return new ListResponse.Plain<>(service.all());
	}

	@GetMapping("/{id}")
	@RequiresPermission("production_product_type.view")
	DataResponse<ProductionProductTypeResponse> show(@PathVariable long id) {
		return DataResponse.of(service.get(id));
	}

	/** Tipin kategorisini (tip ozelinde ya da tip belirtmeden) girdi ya da cikti olarak kullanan hatlar. */
	@GetMapping("/{id}/production-lines")
	@RequiresPermission("production_product_type.view")
	ListResponse<ProductionLineResponse> productionLines(@PathVariable long id) {
		return new ListResponse.Plain<>(lines.usingProductType(service.productIdOf(id), id));
	}

	@GetMapping("/{id}/flow")
	@RequiresPermission("production_product_type.view")
	DataResponse<ProductTypeFlowResponse> flow(@PathVariable long id) {
		ProductionProductTypeResponse type = service.get(id);
		List<ProductionLineResponse> inputs = lines.withInputType(type.productionProductId(), id);
		List<ProductionLineResponse> outputs = lines.withOutputType(type.productionProductId(), id);
		return DataResponse.of(new ProductTypeFlowResponse(type, inputs, outputs,
			ProductionLineResources.merge(inputs, outputs)));
	}

	@PostMapping
	@RequiresPermission("production_product_type.manage")
	ResponseEntity<DataResponse<ProductionProductTypeResponse>> store(
		@Valid @RequestBody CreateProductionProductTypeRequest request) {
		return DataResponse.created(service.create(request));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_product_type.manage")
	DataResponse<ProductionProductTypeResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateProductionProductTypeRequest request) {
		return DataResponse.of(service.update(id, request));
	}

	@DeleteMapping("/{id}")
	@RequiresPermission("production_product_type.manage")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "ProductionProductType deleted successfully.");
	}

}
