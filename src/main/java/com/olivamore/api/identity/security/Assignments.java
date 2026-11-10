package com.olivamore.api.identity.security;

import com.olivamore.api.identity.role.RoleRepository;
import com.olivamore.api.identity.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Kullanicinin ve rolun yetkileri, id sirasinda; her istekte veritabanindan okunur. */
@Component
public class Assignments {

	private final UserRepository users;
	private final RoleRepository roles;

	public Assignments(UserRepository users, RoleRepository roles) {
		this.users = users;
		this.roles = roles;
	}

	public List<String> roleNames(long userId) {
		return users.roleNames(userId);
	}

	public boolean hasAnyRole(long userId, List<String> names) {
		return roleNames(userId).stream().anyMatch(names::contains);
	}

	/** Dogrudan ve rollerden gelen izinler, tekrarsiz. */
	public List<String> permissionNames(long userId) {
		return users.permissionNames(userId);
	}

	public List<String> rolePermissionNames(long roleId) {
		return roles.permissionNames(roleId);
	}

}
