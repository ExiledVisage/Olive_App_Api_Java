package com.olivamore.api.productionline.stage;

import com.olivamore.api.productionline.ProductionStageResponse;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production-lines/{lineId}/stages")
@PreAuthorize("hasAuthority('production_stage.manage')")
class ProductionStageController {

	private final ProductionStageService service;
	private final ProductionStageResponses responses;

	ProductionStageController(ProductionStageService service, ProductionStageResponses responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping
	ListResponse<ProductionStageResponse> index(@PathVariable long lineId) {
		return new ListResponse<>(responses.of(service.forLine(lineId), true));
	}

	@GetMapping("/{id}")
	DataResponse<ProductionStageResponse> show(@PathVariable long lineId, @PathVariable long id) {
		return DataResponse.of(one(service.get(lineId, id)));
	}

	@PostMapping
	ResponseEntity<DataResponse<ProductionStageResponse>> store(@PathVariable long lineId,
		@Valid @RequestBody CreateProductionStageRequest request) {
		return DataResponse.created(one(service.create(lineId, request)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<ProductionStageResponse> update(@PathVariable long lineId, @PathVariable long id,
		@Valid @RequestBody UpdateProductionStageRequest request) {
		return DataResponse.of(one(service.update(lineId, id, request)));
	}

	@DeleteMapping("/{id}")
	Map<String, String> destroy(@PathVariable long lineId, @PathVariable long id) {
		service.delete(lineId, id);
		return Map.of("message", "Production stage deleted successfully.");
	}

	private ProductionStageResponse one(ProductionStage stage) {
		return responses.of(List.of(stage), true).getFirst();
	}

}
