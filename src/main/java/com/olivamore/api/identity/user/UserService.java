package com.olivamore.api.identity.user;

import com.olivamore.api.identity.auth.AuthService;
import com.olivamore.api.identity.security.Assignments;
import com.olivamore.api.identity.permission.Permission;
import com.olivamore.api.identity.permission.PermissionRepository;
import com.olivamore.api.identity.role.Role;
import com.olivamore.api.identity.role.RoleRepository;
import com.olivamore.api.identity.user.User;
import com.olivamore.api.identity.user.UserRepository;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Laravel {@code UserService} ve {@code UserRoleService}: kullanicilar ve rolleri. Admin kullanici silinemez, admin rolu ondan kaldirilamaz. */
@Service
class UserService {

	static final String ADMIN_ROLE_REMOVAL = "Admin kullanicisindan admin rolu kaldirilamaz.";
	static final String ADMIN_USER_DELETE = "Admin kullanicisi silinemez.";

	private final UserRepository users;
	private final RoleRepository roles;
	private final Assignments assignments;

	UserService(UserRepository users, RoleRepository roles, Assignments assignments) {
		this.users = users;
		this.roles = roles;
		this.assignments = assignments;
	}

	@Transactional
	User createUser(String name, String email, String password, List<String> roleNames) {
		User user = new User();
		user.setName(name);
		user.setEmail(email);
		user.setPassword(AuthService.hash(password));
		user.setCreatedAt(Timestamps.now());
		user.setUpdatedAt(user.getCreatedAt());
		users.save(user);
		assignments.syncUserRoles(user.getId(), roleIds(roleNames == null ? List.of() : roleNames));
		return user;
	}

	/**
	 * {@code roles} anahtari geldiyse roller senkronlanir (null = hepsi kaldirilir). Sifre bossa degismez.
	 * Eloquent {@code update} gibi: bir sey degismediyse {@code updated_at} da degismez.
	 */
	@Transactional
	User updateUser(User user, String name, String email, String password, boolean rolesGiven, List<String> roleNames) {
		boolean dirty = !Objects.equals(user.getName(), name) || !Objects.equals(user.getEmail(), email);
		user.setName(name);
		user.setEmail(email);
		if (password != null && !password.isEmpty()) {
			user.setPassword(AuthService.hash(password));
			dirty = true;
		}
		if (dirty) {
			user.setUpdatedAt(Timestamps.now());
		}
		// @Bound ile gelen entity transaction disinda yuklendi: save (merge) degisikligi yazar
		user = users.save(user);
		if (rolesGiven) {
			syncRoles(user, roleNames == null ? List.of() : roleNames);
		}
		return user;
	}

	/** {@code UserRoleService::syncRoles} */
	@Transactional
	User syncRoles(User user, List<String> roleNames) {
		if (assignments.hasAnyRole(user.getId(), List.of(Role.ADMIN)) && !roleNames.contains(Role.ADMIN)) {
			throw HttpStatusException.unprocessable(ADMIN_ROLE_REMOVAL);
		}
		assignments.syncUserRoles(user.getId(), roleIds(roleNames));
		return user;
	}

	@Transactional
	void deleteUser(User user) {
		if (assignments.hasAnyRole(user.getId(), List.of(Role.ADMIN))) {
			throw HttpStatusException.unprocessable(ADMIN_USER_DELETE);
		}
		assignments.detachUser(user.getId());
		users.delete(user);
	}

	private List<Long> roleIds(List<String> names) {
		Map<String, Long> byName = roles.findByNameInAndGuardName(names, Role.GUARD).stream()
			.collect(Collectors.toMap(Role::getName, Role::getId));
		return names.stream().map(n -> require(byName, n, "role")).toList();
	}

	/** Spatie RoleDoesNotExist / PermissionDoesNotExist: validation ({@code exists}) gecse de guard tutmazsa 500. */
	private static Long require(Map<String, Long> byName, String name, String kind) {
		Long id = byName.get(name);
		if (id == null) {
			throw new IllegalStateException("There is no " + kind + " named `" + name + "` for guard `" + Role.GUARD + "`.");
		}
		return id;
	}

}
