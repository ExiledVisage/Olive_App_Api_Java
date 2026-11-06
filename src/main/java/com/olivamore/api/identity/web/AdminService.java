package com.olivamore.api.identity.web;

import com.olivamore.api.identity.auth.AuthService;
import com.olivamore.api.identity.domain.Assignments;
import com.olivamore.api.identity.domain.Permission;
import com.olivamore.api.identity.domain.PermissionRepository;
import com.olivamore.api.identity.domain.Role;
import com.olivamore.api.identity.domain.RoleRepository;
import com.olivamore.api.identity.domain.User;
import com.olivamore.api.identity.domain.UserRepository;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Laravel {@code UserService}, {@code UserRoleService} ve {@code RoleService}. */
@Service
class AdminService {

	static final String ADMIN_ROLE_REMOVAL = "Admin kullanicisindan admin rolu kaldirilamaz.";
	static final String ADMIN_USER_DELETE = "Admin kullanicisi silinemez.";
	static final String ADMIN_ROLE_RENAME = "Admin rolu yeniden adlandirilamaz.";
	static final String ADMIN_ROLE_DELETE = "Admin rolu silinemez.";

	private final UserRepository users;
	private final RoleRepository roles;
	private final PermissionRepository permissions;
	private final Assignments assignments;

	AdminService(UserRepository users, RoleRepository roles, PermissionRepository permissions, Assignments assignments) {
		this.users = users;
		this.roles = roles;
		this.permissions = permissions;
		this.assignments = assignments;
	}

	// ---- kullanicilar ------------------------------------------------------

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

	// ---- roller ------------------------------------------------------------

	@Transactional
	Role createRole(String name, List<String> permissionNames) {
		Role role = new Role();
		role.setName(name);
		role.setGuardName(Role.GUARD);
		role.setCreatedAt(Timestamps.now());
		role.setUpdatedAt(role.getCreatedAt());
		roles.save(role);
		assignments.syncRolePermissions(role.getId(), permissionIds(permissionNames == null ? List.of() : permissionNames));
		return role;
	}

	/** {@code permissions} gelmediyse rolun butun izinleri kaldirilir ({@code syncPermissions($data['permissions'] ?? [])}). */
	@Transactional
	Role updateRole(Role role, String name, List<String> permissionNames) {
		if (Role.ADMIN.equals(role.getName()) && !Role.ADMIN.equals(name)) {
			throw HttpStatusException.unprocessable(ADMIN_ROLE_RENAME);
		}
		if (!Objects.equals(role.getName(), name)) {
			role.setName(name);
			role.setUpdatedAt(Timestamps.now());
		}
		role = roles.save(role);
		assignments.syncRolePermissions(role.getId(), permissionIds(permissionNames == null ? List.of() : permissionNames));
		return role;
	}

	@Transactional
	void deleteRole(Role role) {
		if (Role.ADMIN.equals(role.getName())) {
			throw HttpStatusException.unprocessable(ADMIN_ROLE_DELETE);
		}
		// model_has_roles ve role_has_permissions FK ile (ON DELETE CASCADE) silinir
		roles.delete(role);
		assignments.forgetLaravelPermissionCache();
	}

	// ---- isimden id'ye (Spatie getStoredRole / getStoredPermission) --------

	private List<Long> roleIds(List<String> names) {
		Map<String, Long> byName = roles.findByNameInAndGuardName(names, Role.GUARD).stream()
			.collect(Collectors.toMap(Role::getName, Role::getId));
		return names.stream().map(n -> require(byName, n, "role")).toList();
	}

	private List<Long> permissionIds(List<String> names) {
		Map<String, Long> byName = permissions.findByNameInAndGuardName(names, Role.GUARD).stream()
			.collect(Collectors.toMap(Permission::getName, Permission::getId, (a, b) -> a));
		return names.stream().map(n -> require(byName, n, "permission")).toList();
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
