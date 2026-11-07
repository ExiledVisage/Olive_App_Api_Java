package com.olivamore.api.identity.role;

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

/** Laravel {@code RoleService}: roller ve izinleri. Admin rolu yeniden adlandirilamaz ve silinemez. */
@Service
class RoleService {

	static final String ADMIN_ROLE_RENAME = "Admin rolu yeniden adlandirilamaz.";
	static final String ADMIN_ROLE_DELETE = "Admin rolu silinemez.";

	private final RoleRepository roles;
	private final PermissionRepository permissions;
	private final Assignments assignments;

	RoleService(RoleRepository roles, PermissionRepository permissions, Assignments assignments) {
		this.roles = roles;
		this.permissions = permissions;
		this.assignments = assignments;
	}

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
