package com.olivamore.api.identity.user;

import com.olivamore.api.identity.security.Assignments;
import org.springframework.stereotype.Component;

@Component
public class UserResponses {

	private final Assignments assignments;

	UserResponses(Assignments assignments) {
		this.assignments = assignments;
	}

	public UserResponse of(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), assignments.roleNames(user.getId()),
			assignments.permissionNames(user.getId()), user.getCreatedAt());
	}

	public RawUserResponse raw(User user) {
		return new RawUserResponse(user.getId(), user.getName(), user.getEmail(), user.getEmailVerifiedAt(),
			user.getCreatedAt(), user.getUpdatedAt());
	}

}
