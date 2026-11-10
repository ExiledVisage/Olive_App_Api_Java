package com.olivamore.api.identity.auth;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record LoginRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Email(message = MessageKeys.EMAIL)
	String email,

	@NotBlank(message = MessageKeys.REQUIRED)
	String password) {

	LoginRequest {
		email = Texts.trim(email);
	}

}
