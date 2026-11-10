package com.olivamore.api.identity.role;

import com.olivamore.api.identity.security.Assignments;
import org.springframework.stereotype.Component;

@Component
class RoleResponses {

	private final Assignments assignments;

	RoleResponses(Assignments assignments) {
		this.assignments = assignments;
	}

	RoleResponse of(Role role) {
		return new RoleResponse(role.getId(), role.getName(), assignments.rolePermissionNames(role.getId()));
	}

}
