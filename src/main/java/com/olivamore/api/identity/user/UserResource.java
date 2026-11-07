package com.olivamore.api.identity.user;

import com.olivamore.api.identity.security.Assignments;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Laravel {@code UserResource}: roller ve butun izinler (dogrudan + rollerden). */
@Component
public class UserResource {

	private final Assignments assignments;

	UserResource(Assignments assignments) {
		this.assignments = assignments;
	}

	public record Data(Long id, String name, String email, List<String> roles, List<String> permissions, LocalDateTime createdAt) {
	}

	/**
	 * {@code GET /api/user}: Resource degil, modelin kendisi ({@code $hidden} = password, remember_token). Alan sirasi
	 * tablodaki kolon sirasi.
	 */
	public record Raw(Long id, String name, String email, LocalDateTime emailVerifiedAt, LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	}

	public Data of(User user) {
		return new Data(user.getId(), user.getName(), user.getEmail(), assignments.roleNames(user.getId()),
			assignments.permissionNames(user.getId()), user.getCreatedAt());
	}

	public Raw raw(User user) {
		return new Raw(user.getId(), user.getName(), user.getEmail(), user.getEmailVerifiedAt(), user.getCreatedAt(),
			user.getUpdatedAt());
	}

}
