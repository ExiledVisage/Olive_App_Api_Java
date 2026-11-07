package com.olivamore.api.identity.role;

import com.olivamore.api.identity.RequiresRole;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Laravel {@code RoleController} ({@code role:admin}). Liste sayfasiz, ada gore. */
@RestController
@RequiresRole(Role.ADMIN)
class RoleController {

	private final RoleService service;
	private final RoleRepository roles;
	private final RoleResource resource;
	private final LaravelValidator validator;

	RoleController(RoleService service, RoleRepository roles, RoleResource resource, LaravelValidator validator) {
		this.service = service;
		this.roles = roles;
		this.resource = resource;
		this.validator = validator;
	}

	@GetMapping("/api/roles")
	ListResponse<RoleResource.Data> index() {
		return new ListResponse.Plain<>(roles.findAllByOrderByNameAsc().stream().map(resource::of).toList());
	}

	@PostMapping("/api/roles")
	ResponseEntity<DataResponse<RoleResource.Data>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, RoleRequests.store());
		return DataResponse.created(resource.of(service.createRole(v.string("name"), v.strings("permissions"))));
	}

	@GetMapping("/api/roles/{role}")
	DataResponse<RoleResource.Data> show(@Bound Role role) {
		return DataResponse.of(resource.of(role));
	}

	@RequestMapping(path = "/api/roles/{role}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<RoleResource.Data> update(@Bound Role role, RequestInput input) {
		ValidatedInput v = validator.validate(input, RoleRequests.update(role.getId()));
		return DataResponse.of(resource.of(service.updateRole(role, v.string("name"), v.strings("permissions"))));
	}

	@DeleteMapping("/api/roles/{role}")
	Map<String, String> destroy(@Bound Role role) {
		service.deleteRole(role);
		return Map.of("message", "Role deleted successfully.");
	}

}
