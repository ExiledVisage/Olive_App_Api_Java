package com.olivamore.api.identity.auth;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotBlank;

record RefreshTokenRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	String refreshToken) {
}
