package com.olivamore.api.productionline.line;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Laravel {@code ProductionLineController} (production_line.view / production_line.manage). */
@RestController
class ProductionLineController {

	private final LaravelValidator validator;
	private final ProductionLineService lines;
	private final ProductionLineResource resource;

	ProductionLineController(LaravelValidator validator, ProductionLineService lines, ProductionLineResource resource) {
		this.validator = validator;
		this.lines = lines;
		this.resource = resource;
	}

	@GetMapping("/api/production-lines")
	@RequiresPermission("production_line.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(resource.lines(lines.all(), ProductionLineResource.LineLoad.FULL));
	}

	@GetMapping("/api/production-lines/{production_line}")
	@RequiresPermission("production_line.view")
	DataResponse<Map<String, Object>> show(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line) {
		return DataResponse.of(resource.line(line, ProductionLineResource.LineLoad.FULL));
	}

	/** Cevap 200: {@code getById($productionLine->fresh())}. */
	@PostMapping("/api/production-lines")
	@RequiresPermission("production_line.manage")
	@Transactional
	DataResponse<Map<String, Object>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionLineRequests.store());
		return DataResponse.of(resource.line(lines.create(v), ProductionLineResource.LineLoad.FULL));
	}

	@RequestMapping(path = "/api/production-lines/{production_line}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("production_line.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionLineRequests.update());
		return DataResponse.of(resource.line(lines.update(line, v), ProductionLineResource.LineLoad.FULL));
	}

	@DeleteMapping("/api/production-lines/{production_line}")
	@RequiresPermission("production_line.manage")
	@Transactional
	Map<String, String> destroy(
		@Bound(value = "production_line", table = "production_lines", model = "ProductionLine") Row line) {
		lines.delete(line);
		return Map.of("message", "Production line deleted successfully.");
	}

}
