package com.olivamore.api.productionline.stage;

import com.olivamore.api.identity.RequiresPermission;
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

/** Laravel {@code ProductionStageController}: bir hattin asamalari (production_stage.manage). */
@RestController
class ProductionStageController {

	private final LaravelValidator validator;
	private final ProductionStageService stages;
	private final ProductionStageResource resource;

	ProductionStageController(LaravelValidator validator, ProductionStageService stages, ProductionStageResource resource) {
		this.validator = validator;
		this.stages = stages;
		this.resource = resource;
	}

	@GetMapping("/api/production-lines/{production_line}/stages")
	@RequiresPermission("production_stage.manage")
	ListResponse<Map<String, Object>> index(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line) {
		return new ListResponse.Plain<>(resource.stages(stages.forLine(line), true, false));
	}

	@GetMapping("/api/production-lines/{production_line}/stages/{stage}")
	@RequiresPermission("production_stage.manage")
	DataResponse<Map<String, Object>> show(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage) {
		ProductionStageService.ensureBelongs(line, stage);
		return DataResponse.of(one(stage));
	}

	@PostMapping("/api/production-lines/{production_line}/stages")
	@RequiresPermission("production_stage.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionStageRequests.store());
		return DataResponse.created(one(stages.create(line.id(), v.all())));
	}

	@RequestMapping(path = "/api/production-lines/{production_line}/stages/{stage}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_stage.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionStageRequests.update());
		ProductionStageService.ensureBelongs(line, stage);
		return DataResponse.of(one(stages.update(line.id(), stage, v.all())));
	}

	@DeleteMapping("/api/production-lines/{production_line}/stages/{stage}")
	@RequiresPermission("production_stage.manage")
	@Transactional
	Map<String, String> destroy(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage) {
		ProductionStageService.ensureBelongs(line, stage);
		stages.delete(line.id(), stage);
		return Map.of("message", "Production stage deleted successfully.");
	}

	private Map<String, Object> one(Row stage) {
		return resource.stages(List.of(stage), true, false).getFirst();
	}

}
