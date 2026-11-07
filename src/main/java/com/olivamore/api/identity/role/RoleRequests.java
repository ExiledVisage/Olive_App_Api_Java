package com.olivamore.api.identity.role;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/Role}: StoreRoleRequest, UpdateRoleRequest. */
final class RoleRequests {

	private RoleRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("name", "required|string|max:255|unique:roles,name")
			.field("permissions", "nullable|array")
			.field("permissions.*", "string|exists:permissions,name");
	}

	static Rules update(long roleId) {
		return Rules.of()
			.field("name", "required|string|max:255|unique:roles,name," + roleId)
			.field("permissions", "nullable|array")
			.field("permissions.*", "string|exists:permissions,name");
	}

}
