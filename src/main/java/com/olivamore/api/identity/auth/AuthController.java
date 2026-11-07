package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Public;
import com.olivamore.api.identity.user.User;
import com.olivamore.api.identity.user.UserRepository;
import com.olivamore.api.identity.user.UserResource;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.throttle.Throttle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Laravel {@code AuthController} ve {@code GET /api/user}. */
@RestController
class AuthController {

	private final AuthService auth;
	private final UserRepository users;
	private final UserResource resources;
	private final LaravelValidator validator;

	AuthController(AuthService auth, UserRepository users, UserResource resources, LaravelValidator validator) {
		this.auth = auth;
		this.users = users;
		this.resources = resources;
		this.validator = validator;
	}

	/** RegisterRequest */
	@Public
	@Throttle("register")
	@PostMapping("/api/auth/register")
	ResponseEntity<Map<String, Object>> register(RequestInput input) {
		ValidatedInput v = validator.validate(input, AuthRequests.register());
		AuthService.Result result = auth.register(v.string("name"), v.string("email"), v.string("password"));
		return ResponseEntity.status(HttpStatus.CREATED).body(tokenResponse("User registered successfully.", result));
	}

	/** LoginRequest */
	@Public
	@Throttle("login")
	@PostMapping("/api/auth/login")
	Map<String, Object> login(RequestInput input) {
		ValidatedInput v = validator.validate(input, AuthRequests.login());
		return tokenResponse("Login successful.", auth.login(v.string("email"), v.string("password")));
	}

	@PostMapping("/api/auth/logout")
	Map<String, Object> logout(CurrentUser user) {
		auth.logout(user.tokenId());
		return Map.of("message", "Logged out successfully.");
	}

	@GetMapping("/api/auth/me")
	DataResponse<UserResource.Data> me(CurrentUser user) {
		return DataResponse.of(resources.of(load(user)));
	}

	/** Laravel'de closure: {@code return $request->user();} (Resource degil, model). */
	@GetMapping("/api/user")
	UserResource.Raw user(CurrentUser user) {
		return resources.raw(load(user));
	}

	private User load(CurrentUser user) {
		return users.findById(user.id()).orElseThrow();
	}

	private Map<String, Object> tokenResponse(String message, AuthService.Result result) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", message);
		body.put("token", result.token());
		body.put("user", resources.of(result.user()));
		return body;
	}

}
