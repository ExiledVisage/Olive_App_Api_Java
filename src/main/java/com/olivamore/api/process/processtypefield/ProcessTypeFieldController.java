package com.olivamore.api.process.processtypefield;

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

import java.util.Map;

/** Laravel {@code ProcessTypeFieldController}: bir proses tipinin alanlari (process_type_field.manage). */
@RestController
class ProcessTypeFieldController {

	private final LaravelValidator validator;
	private final ProcessTypeFieldService fields;
	private final ProcessTypeFieldResource resource;

	ProcessTypeFieldController(LaravelValidator validator, ProcessTypeFieldService fields, ProcessTypeFieldResource resource) {
		this.validator = validator;
		this.fields = fields;
		this.resource = resource;
	}

	@GetMapping("/api/process-types/{process_type}/fields")
	@RequiresPermission("process_type_field.manage")
	ListResponse<Map<String, Object>> index(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType) {
		return new ListResponse.Plain<>(resource.fields(fields.forProcessType(processType)));
	}

	@GetMapping("/api/process-types/{process_type}/fields/{field}")
	@RequiresPermission("process_type_field.manage")
	DataResponse<Map<String, Object>> show(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType,
		@Bound(table = "process_type_fields", model = "ProcessTypeField") Row field) {
		ProcessTypeFieldService.ensureBelongs(processType, field);
		return DataResponse.of(ProcessTypeFieldResource.of(field));
	}

	@PostMapping("/api/process-types/{process_type}/fields")
	@RequiresPermission("process_type_field.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProcessTypeFieldRequests.store(processType.id()));
		return DataResponse.created(ProcessTypeFieldResource.of(fields.create(processType, v.all())));
	}

	/** Once validation (FormRequest), sonra "bu prosese ait mi" (404). */
	@RequestMapping(path = "/api/process-types/{process_type}/fields/{field}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("process_type_field.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType,
		@Bound(table = "process_type_fields", model = "ProcessTypeField") Row field, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProcessTypeFieldRequests.update(processType.id(), field.id()));
		ProcessTypeFieldService.ensureBelongs(processType, field);
		return DataResponse.of(ProcessTypeFieldResource.of(fields.update(field, v.all())));
	}

	@DeleteMapping("/api/process-types/{process_type}/fields/{field}")
	@RequiresPermission("process_type_field.manage")
	@Transactional
	Map<String, String> destroy(
		@Bound(value = "process_type", table = "process_types", model = "ProcessType") Row processType,
		@Bound(table = "process_type_fields", model = "ProcessTypeField") Row field) {
		ProcessTypeFieldService.ensureBelongs(processType, field);
		fields.delete(field);
		return Map.of("message", "Process type field deleted successfully.");
	}

}
