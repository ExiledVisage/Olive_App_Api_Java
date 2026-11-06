package com.olivamore.api.identity.web;

import com.olivamore.api.identity.domain.Assignments;
import com.olivamore.api.identity.domain.Permission;
import com.olivamore.api.identity.domain.Role;
import com.olivamore.api.identity.domain.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Laravel'in JsonResource'lari: UserResource, RoleResource, PermissionResource. */
@Component
public class Resources {

	private final Assignments assignments;

	Resources(Assignments assignments) {
		this.assignments = assignments;
	}

	/** {@code UserResource}: roller ve butun izinler (dogrudan + rollerden). */
	public record UserResource(Long id, String name, String email, List<String> roles, List<String> permissions,
		LocalDateTime createdAt) {
	}

	/** {@code RoleResource} */
	public record RoleResource(Long id, String name, List<String> permissions) {
	}

	/** {@code PermissionResource} */
	public record PermissionResource(Long id, String name) {
	}

	/**
	 * {@code GET /api/user}: Resource degil, modelin kendisi ({@code $hidden} = password, remember_token).
	 * Alan sirasi tablodaki kolon sirasi.
	 */
	public record RawUser(Long id, String name, String email, LocalDateTime emailVerifiedAt, LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	}

	public UserResource user(User user) {
		return new UserResource(user.getId(), user.getName(), user.getEmail(), assignments.roleNames(user.getId()),
			assignments.permissionNames(user.getId()), user.getCreatedAt());
	}

	public RoleResource role(Role role) {
		return new RoleResource(role.getId(), role.getName(), assignments.rolePermissionNames(role.getId()));
	}

	public PermissionResource permission(Permission permission) {
		return new PermissionResource(permission.getId(), permission.getName());
	}

	public RawUser raw(User user) {
		return new RawUser(user.getId(), user.getName(), user.getEmail(), user.getEmailVerifiedAt(), user.getCreatedAt(),
			user.getUpdatedAt());
	}

}
