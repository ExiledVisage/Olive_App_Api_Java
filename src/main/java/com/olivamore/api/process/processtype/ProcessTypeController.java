package com.olivamore.api.process.processtype;

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

/** Laravel {@code ProcessTypeController} (process_type.manage). */
@RestController
class ProcessTypeController {

	private final LaravelValidator validator;
	private final ProcessTypeService processTypes;
	private final ProcessTypeResource resource;

	ProcessTypeController(LaravelValidator validator, ProcessTypeService processTypes, ProcessTypeResource resource) {
		this.validator = validator;
		this.processTypes = processTypes;
		this.resource = resource;
	}

	@GetMapping("/api/process-types")
	@RequiresPermission("process_type.manage")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(resource.processTypes(processTypes.all()));
	}

	@GetMapping("/api/process-types/{process_type}")
	@RequiresPermission("process_type.manage")
	DataResponse<Map<String, Object>> show(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType) {
		return DataResponse.of(one(processType));
	}

	@PostMapping("/api/process-types")
	@RequiresPermission("process_type.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, ProcessTypeRequests.store());
		return DataResponse.created(one(processTypes.create(v.all())));
	}

	@RequestMapping(path = "/api/process-types/{process_type}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("process_type.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProcessTypeRequests.update(processType.id()));
		return DataResponse.of(one(processTypes.update(processType, v.all())));
	}

	@DeleteMapping("/api/process-types/{process_type}")
	@RequiresPermission("process_type.manage")
	@Transactional
	Map<String, String> destroy(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType) {
		processTypes.delete(processType);
		return Map.of("message", "Process type deleted successfully.");
	}

	private Map<String, Object> one(Row processType) {
		return resource.processTypes(List.of(processType)).getFirst();
	}

}
