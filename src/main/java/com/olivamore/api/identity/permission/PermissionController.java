package com.olivamore.api.identity.permission;

import com.olivamore.api.identity.RequiresRole;
import com.olivamore.api.identity.role.Role;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Laravel {@code PermissionController} ({@code role:admin}): izinler, ada gore. */
@RestController
@RequiresRole(Role.ADMIN)
class PermissionController {

	private final PermissionRepository permissions;

	PermissionController(PermissionRepository permissions) {
		this.permissions = permissions;
	}

	@GetMapping("/api/permissions")
	ListResponse<PermissionResource.Data> index() {
		return new ListResponse.Plain<>(permissions.findAllByOrderByNameAsc().stream().map(PermissionResource::of).toList());
	}

}
