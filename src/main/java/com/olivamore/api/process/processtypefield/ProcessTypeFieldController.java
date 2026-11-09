package com.olivamore.api.process.processtypefield;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.process.ProcessTypeFieldResponse;
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

import java.util.Map;

@RestController
@RequestMapping("/api/process-types/{processTypeId}/fields")
@RequiresPermission("process_type_field.manage")
class ProcessTypeFieldController {

	private final ProcessTypeFieldService service;

	ProcessTypeFieldController(ProcessTypeFieldService service) {
		this.service = service;
	}

	@GetMapping
	ListResponse<ProcessTypeFieldResponse> index(@PathVariable long processTypeId) {
		return new ListResponse.Plain<>(
			service.forProcessType(processTypeId).stream().map(ProcessTypeFieldResponses::of).toList());
	}

	@GetMapping("/{id}")
	DataResponse<ProcessTypeFieldResponse> show(@PathVariable long processTypeId, @PathVariable long id) {
		return DataResponse.of(ProcessTypeFieldResponses.of(service.get(processTypeId, id)));
	}

	@PostMapping
	ResponseEntity<DataResponse<ProcessTypeFieldResponse>> store(@PathVariable long processTypeId,
		@Valid @RequestBody CreateProcessTypeFieldRequest request) {
		return DataResponse.created(ProcessTypeFieldResponses.of(service.create(processTypeId, request)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<ProcessTypeFieldResponse> update(@PathVariable long processTypeId, @PathVariable long id,
		@Valid @RequestBody UpdateProcessTypeFieldRequest request) {
		return DataResponse.of(ProcessTypeFieldResponses.of(service.update(processTypeId, id, request)));
	}

	@DeleteMapping("/{id}")
	Map<String, String> destroy(@PathVariable long processTypeId, @PathVariable long id) {
		service.delete(processTypeId, id);
		return Map.of("message", "Process type field deleted successfully.");
	}

}
