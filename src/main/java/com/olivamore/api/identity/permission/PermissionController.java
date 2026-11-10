package com.olivamore.api.identity.permission;

import com.olivamore.api.identity.role.Role;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('" + Role.ADMIN + "')")
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
