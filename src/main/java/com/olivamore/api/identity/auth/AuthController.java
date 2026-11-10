package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.user.RawUserResponse;
import com.olivamore.api.identity.user.UserResponse;
import com.olivamore.api.identity.user.UserResponses;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.throttle.Throttle;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class AuthController {

	private static final String TOKEN_TYPE = "Bearer";

	private final AuthService auth;
	private final UserResponses responses;

	AuthController(AuthService auth, UserResponses responses) {
		this.auth = auth;
		this.responses = responses;
	}

	@Throttle("register")
	@PostMapping("/api/auth/register")
	ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(tokenResponse("User registered successfully.", auth.register(request)));
	}

	@Throttle("login")
	@PostMapping("/api/auth/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return tokenResponse("Login successful.", auth.login(request));
	}

	@Throttle("refresh")
	@PostMapping("/api/auth/refresh")
	RefreshResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		TokenService.Tokens tokens = auth.refresh(request.refreshToken());
		return new RefreshResponse("Token refreshed successfully.", tokens.accessToken(), tokens.refreshToken(),
			TOKEN_TYPE, tokens.expiresIn());
	}

	@PostMapping("/api/auth/logout")
	Map<String, String> logout(@AuthenticationPrincipal CurrentUser user) {
		auth.logout(user.sessionId());
		return Map.of("message", "Logged out successfully.");
	}

	@GetMapping("/api/auth/me")
	DataResponse<UserResponse> me(@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(responses.of(auth.current(user.id())));
	}

	/** Roller olmadan, yalnizca hesap alanlari. */
	@GetMapping("/api/user")
	RawUserResponse user(@AuthenticationPrincipal CurrentUser user) {
		return responses.raw(auth.current(user.id()));
	}

	private TokenResponse tokenResponse(String message, AuthService.Result result) {
		TokenService.Tokens tokens = result.tokens();
		return new TokenResponse(message, tokens.accessToken(), tokens.refreshToken(), TOKEN_TYPE, tokens.expiresIn(),
			responses.of(result.user()));
	}

}
