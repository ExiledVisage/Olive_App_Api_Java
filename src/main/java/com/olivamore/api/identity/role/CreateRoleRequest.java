package com.olivamore.api.identity.role;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateRoleRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	List<String> permissions) {
}
