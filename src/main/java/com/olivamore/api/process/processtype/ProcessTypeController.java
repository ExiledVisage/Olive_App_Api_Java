package com.olivamore.api.process.processtype;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.process.ProcessTypeResponse;
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
@RequestMapping("/api/process-types")
@RequiresPermission("process_type.manage")
class ProcessTypeController {

	private final ProcessTypeService service;
	private final ProcessTypeResponses responses;

	ProcessTypeController(ProcessTypeService service, ProcessTypeResponses responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping
	ListResponse<ProcessTypeResponse> index() {
		return new ListResponse.Plain<>(responses.of(service.all()));
	}

	@GetMapping("/{id}")
	DataResponse<ProcessTypeResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.of(service.get(id)));
	}

	@PostMapping
	ResponseEntity<DataResponse<ProcessTypeResponse>> store(@Valid @RequestBody CreateProcessTypeRequest request) {
		return DataResponse.created(responses.of(service.create(request)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<ProcessTypeResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateProcessTypeRequest request) {
		return DataResponse.of(responses.of(service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Process type deleted successfully.");
	}

}
