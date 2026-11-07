package com.olivamore.api.identity.auth;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/Auth}: RegisterRequest, LoginRequest. */
final class AuthRequests {

	private AuthRequests() {
	}

	static Rules register() {
		return Rules.of()
			.field("name", "required|string|max:255")
			.field("email", "required|string|email|max:255|unique:users,email")
			.field("password", "required|string|min:8|confirmed");
	}

	static Rules login() {
		return Rules.of()
			.field("email", "required|string|email")
			.field("password", "required|string");
	}

}
