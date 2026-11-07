package com.olivamore.api.identity.role;

import com.olivamore.api.identity.security.Assignments;
import org.springframework.stereotype.Component;

import java.util.List;

/** Laravel {@code RoleResource}: rolun izin adlari. */
@Component
class RoleResource {

	private final Assignments assignments;

	RoleResource(Assignments assignments) {
		this.assignments = assignments;
	}

	record Data(Long id, String name, List<String> permissions) {
	}

	Data of(Role role) {
		return new Data(role.getId(), role.getName(), assignments.rolePermissionNames(role.getId()));
	}

}
