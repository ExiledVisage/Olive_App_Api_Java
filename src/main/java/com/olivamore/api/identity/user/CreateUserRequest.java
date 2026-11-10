package com.olivamore.api.identity.user;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateUserRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Email(message = LaravelMessages.EMAIL)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String email,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Size(min = 8, message = LaravelMessages.MIN_8)
	String password,

	List<String> roles) {
}
