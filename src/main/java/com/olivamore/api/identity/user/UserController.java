package com.olivamore.api.identity.user;

import com.olivamore.api.identity.RequiresRole;
import com.olivamore.api.identity.role.Role;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiresRole(Role.ADMIN)
class UserController {

	private final UserService service;
	private final UserResponses responses;

	UserController(UserService service, UserResponses responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping
	ListResponse<UserResponse> index() {
		return new ListResponse.Plain<>(service.all().stream().map(responses::of).toList());
	}

	@PostMapping
	ResponseEntity<DataResponse<UserResponse>> store(@Valid @RequestBody CreateUserRequest request) {
		return DataResponse.created(responses.of(service.create(request)));
	}

	@GetMapping("/{id}")
	DataResponse<UserResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.of(service.get(id)));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<UserResponse> update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
		return DataResponse.of(responses.of(service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "User deleted successfully.");
	}

	@PatchMapping("/{id}/roles")
	DataResponse<UserResponse> updateRoles(@PathVariable long id, @Valid @RequestBody UpdateUserRolesRequest request) {
		return DataResponse.of(responses.of(service.updateRoles(id, request)));
	}

}
