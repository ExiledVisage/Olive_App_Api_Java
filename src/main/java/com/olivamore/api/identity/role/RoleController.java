package com.olivamore.api.identity.role;

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

import java.util.Map;

@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('" + Role.ADMIN + "')")
class RoleController {

	private final RoleService service;
	private final RoleResponses responses;

	RoleController(RoleService service, RoleResponses responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping
	ListResponse<RoleResponse> index() {
		return new ListResponse.Plain<>(service.all().stream().map(responses::of).toList());
	}

	@PostMapping
	ResponseEntity<DataResponse<RoleResponse>> store(@Valid @RequestBody CreateRoleRequest request) {
		return DataResponse.created(responses.of(service.create(request)));
	}

	@GetMapping("/{id}")
	DataResponse<RoleResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.of(service.get(id)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<RoleResponse> update(@PathVariable long id, @Valid @RequestBody UpdateRoleRequest request) {
		return DataResponse.of(responses.of(service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Role deleted successfully.");
	}

}
