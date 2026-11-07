package com.olivamore.api.identity.user;

import com.olivamore.api.identity.RequiresRole;
import com.olivamore.api.identity.role.Role;
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

/** Laravel {@code UserController} ve {@code UserRoleController} ({@code role:admin}). Liste sayfasiz. */
@RestController
@RequiresRole(Role.ADMIN)
class UserController {

	private final UserService service;
	private final UserRepository users;
	private final UserResource resource;
	private final LaravelValidator validator;

	UserController(UserService service, UserRepository users, UserResource resource, LaravelValidator validator) {
		this.service = service;
		this.users = users;
		this.resource = resource;
		this.validator = validator;
	}

	@GetMapping("/api/users")
	ListResponse<UserResource.Data> index() {
		return new ListResponse.Plain<>(users.latest().stream().map(resource::of).toList());
	}

	@PostMapping("/api/users")
	ResponseEntity<DataResponse<UserResource.Data>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, UserRequests.store());
		return DataResponse.created(resource.of(service.createUser(v.string("name"), v.string("email"), v.string("password"), v.strings("roles"))));
	}

	@GetMapping("/api/users/{user}")
	DataResponse<UserResource.Data> show(@Bound User user) {
		return DataResponse.of(resource.of(user));
	}

	@RequestMapping(path = "/api/users/{user}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<UserResource.Data> update(@Bound User user, RequestInput input) {
		ValidatedInput v = validator.validate(input, UserRequests.update(user.getId()));
		service.updateUser(user, v.string("name"), v.string("email"), v.string("password"), v.has("roles"), v.strings("roles"));
		return DataResponse.of(resource.of(user));
	}

	@DeleteMapping("/api/users/{user}")
	Map<String, String> destroy(@Bound User user) {
		service.deleteUser(user);
		return Map.of("message", "User deleted successfully.");
	}

	/** UserRoleController */
	@PatchMapping("/api/users/{user}/roles")
	DataResponse<UserResource.Data> updateRoles(@Bound User user, RequestInput input) {
		ValidatedInput v = validator.validate(input, UserRequests.updateRoles());
		service.syncRoles(user, v.strings("roles"));
		return DataResponse.of(resource.of(user));
	}

}
