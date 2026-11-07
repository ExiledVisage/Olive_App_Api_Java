package com.olivamore.api.identity.user;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/User}: StoreUserRequest, UpdateUserRequest, UpdateUserRolesRequest. */
final class UserRequests {

	private UserRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("name", "required|string|max:255")
			.field("email", "required|email|max:255|unique:users,email")
			.field("password", "required|string|min:8")
			.field("roles", "nullable|array")
			.field("roles.*", "string|exists:roles,name");
	}

	static Rules update(long userId) {
		return Rules.of()
			.field("name", "required|string|max:255")
			.field("email", "required|email|max:255|unique:users,email," + userId)
			.field("password", "nullable|string|min:8")
			.field("roles", "nullable|array")
			.field("roles.*", "string|exists:roles,name");
	}

	static Rules updateRoles() {
		return Rules.of()
			.field("roles", "required|array|min:1")
			.field("roles.*", "string|exists:roles,name");
	}

}
