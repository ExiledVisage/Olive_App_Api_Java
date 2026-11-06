package com.olivamore.api.identity.web;

import com.olivamore.api.identity.RequiresRole;
import com.olivamore.api.identity.domain.PermissionRepository;
import com.olivamore.api.identity.domain.Role;
import com.olivamore.api.identity.domain.RoleRepository;
import com.olivamore.api.identity.domain.User;
import com.olivamore.api.identity.domain.UserRepository;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Laravel'in "Admin Management" route grubu ({@code auth:sanctum} + {@code role:admin}):
 * UserController, UserRoleController, RoleController, PermissionController.
 * Listeler sayfasiz ({@code Resource::collection($items)}).
 */
@RestController
@RequiresRole(Role.ADMIN)
class AdminController {

	private final AdminService admin;
	private final UserRepository users;
	private final RoleRepository roles;
	private final PermissionRepository permissions;
	private final Resources resources;
	private final LaravelValidator validator;

	AdminController(AdminService admin, UserRepository users, RoleRepository roles, PermissionRepository permissions,
		Resources resources, LaravelValidator validator) {
		this.admin = admin;
		this.users = users;
		this.roles = roles;
		this.permissions = permissions;
		this.resources = resources;
		this.validator = validator;
	}

	// ---- PermissionController ----------------------------------------------

	@GetMapping("/api/permissions")
	ListResponse<Resources.PermissionResource> permissions() {
		return new ListResponse.Plain<>(permissions.findAllByOrderByNameAsc().stream().map(resources::permission).toList());
	}

	// ---- UserController ----------------------------------------------------

	@GetMapping("/api/users")
	ListResponse<Resources.UserResource> users() {
		return new ListResponse.Plain<>(users.latest().stream().map(resources::user).toList());
	}

	/** StoreUserRequest */
	@PostMapping("/api/users")
	ResponseEntity<DataResponse<Resources.UserResource>> storeUser(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255")
			.field("email", "required|email|max:255|unique:users,email")
			.field("password", "required|string|min:8")
			.field("roles", "nullable|array")
			.field("roles.*", "string|exists:roles,name"));
		User user = admin.createUser(v.string("name"), v.string("email"), v.string("password"), v.strings("roles"));
		return DataResponse.created(resources.user(user));
	}

	@GetMapping("/api/users/{user}")
	DataResponse<Resources.UserResource> showUser(@Bound User user) {
		return DataResponse.of(resources.user(user));
	}

	/** UpdateUserRequest */
	@RequestMapping(path = "/api/users/{user}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<Resources.UserResource> updateUser(@Bound User user, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255")
			.field("email", "required|email|max:255|unique:users,email," + user.getId())
			.field("password", "nullable|string|min:8")
			.field("roles", "nullable|array")
			.field("roles.*", "string|exists:roles,name"));
		admin.updateUser(user, v.string("name"), v.string("email"), v.string("password"), v.has("roles"), v.strings("roles"));
		return DataResponse.of(resources.user(user));
	}

	@DeleteMapping("/api/users/{user}")
	Map<String, String> destroyUser(@Bound User user) {
		admin.deleteUser(user);
		return Map.of("message", "User deleted successfully.");
	}

	/** UserRoleController + UpdateUserRolesRequest */
	@PatchMapping("/api/users/{user}/roles")
	DataResponse<Resources.UserResource> updateUserRoles(@Bound User user, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("roles", "required|array|min:1")
			.field("roles.*", "string|exists:roles,name"));
		admin.syncRoles(user, v.strings("roles"));
		return DataResponse.of(resources.user(user));
	}

	// ---- RoleController ----------------------------------------------------

	@GetMapping("/api/roles")
	ListResponse<Resources.RoleResource> roles() {
		return new ListResponse.Plain<>(roles.findAllByOrderByNameAsc().stream().map(resources::role).toList());
	}

	/** StoreRoleRequest */
	@PostMapping("/api/roles")
	ResponseEntity<DataResponse<Resources.RoleResource>> storeRole(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255|unique:roles,name")
			.field("permissions", "nullable|array")
			.field("permissions.*", "string|exists:permissions,name"));
		return DataResponse.created(resources.role(admin.createRole(v.string("name"), v.strings("permissions"))));
	}

	@GetMapping("/api/roles/{role}")
	DataResponse<Resources.RoleResource> showRole(@Bound Role role) {
		return DataResponse.of(resources.role(role));
	}

	/** UpdateRoleRequest */
	@RequestMapping(path = "/api/roles/{role}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	DataResponse<Resources.RoleResource> updateRole(@Bound Role role, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255|unique:roles,name," + role.getId())
			.field("permissions", "nullable|array")
			.field("permissions.*", "string|exists:permissions,name"));
		return DataResponse.of(resources.role(admin.updateRole(role, v.string("name"), v.strings("permissions"))));
	}

	@DeleteMapping("/api/roles/{role}")
	Map<String, String> destroyRole(@Bound Role role) {
		admin.deleteRole(role);
		return Map.of("message", "Role deleted successfully.");
	}

}
