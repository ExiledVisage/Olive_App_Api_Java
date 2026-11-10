package com.olivamore.api.identity.user;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

record UpdateUserRolesRequest(
	@NotEmpty(message = MessageKeys.REQUIRED)
	List<String> roles) {
}
