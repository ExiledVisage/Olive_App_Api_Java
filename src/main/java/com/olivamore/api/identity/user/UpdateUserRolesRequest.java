package com.olivamore.api.identity.user;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

record UpdateUserRolesRequest(
	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<String> roles) {
}
