package com.olivamore.api.productionline.line;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.productionline.ProductionLineResponse;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/production-lines")
class ProductionLineController {

	private final ProductionLineService service;
	private final ProductionLineResponses responses;

	ProductionLineController(ProductionLineService service, ProductionLineResponses responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping
	@RequiresPermission("production_line.view")
	ListResponse<ProductionLineResponse> index() {
		return new ListResponse.Plain<>(responses.withExtensions(service.all()));
	}

	@GetMapping("/{id}")
	@RequiresPermission("production_line.view")
	DataResponse<ProductionLineResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.withExtensions(service.get(id)));
	}

	@PostMapping
	@RequiresPermission("production_line.manage")
	DataResponse<ProductionLineResponse> store(@Valid @RequestBody CreateProductionLineRequest request) {
		return DataResponse.of(responses.withExtensions(service.create(request)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_line.manage")
	DataResponse<ProductionLineResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateProductionLineRequest request) {
		return DataResponse.of(responses.withExtensions(service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	@RequiresPermission("production_line.manage")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Production line deleted successfully.");
	}

}
