package com.olivamore.api.identity.user;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateUserRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Email(message = MessageKeys.EMAIL)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String email,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(min = 8, message = MessageKeys.SIZE_MIN)
	String password,

	List<String> roles) {

	CreateUserRequest {
		name = Texts.trim(name);
		email = Texts.trim(email);
	}

}
