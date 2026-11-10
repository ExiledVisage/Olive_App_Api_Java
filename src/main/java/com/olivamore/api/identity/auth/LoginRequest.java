package com.olivamore.api.identity.auth;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record LoginRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Email(message = LaravelMessages.EMAIL)
	String email,

	@NotNull(message = LaravelMessages.REQUIRED)
	String password) {
}
