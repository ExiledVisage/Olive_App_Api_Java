package com.olivamore.api.identity.auth;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotBlank;

record RefreshTokenRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	String refreshToken) {
}
