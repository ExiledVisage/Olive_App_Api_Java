package com.olivamore.api.identity.permission;

import com.olivamore.api.identity.RequiresRole;
import com.olivamore.api.identity.role.Role;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresRole(Role.ADMIN)
class PermissionController {

	private final PermissionRepository permissions;

	PermissionController(PermissionRepository permissions) {
		this.permissions = permissions;
	}

	@GetMapping("/api/permissions")
	ListResponse<PermissionResponse> index() {
		return new ListResponse.Plain<>(
			permissions.findAllByOrderByNameAsc().stream().map(PermissionResponse::of).toList());
	}

}
