package com.olivamore.api.identity.role;

import com.olivamore.api.identity.permission.Permission;
import com.olivamore.api.identity.permission.PermissionRepository;
import com.olivamore.api.identity.security.Assignments;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Roller ve izinleri. Admin rolu yeniden adlandirilamaz ve silinemez. */
@Service
class RoleService {

	static final String ADMIN_ROLE_RENAME = "Admin rolu yeniden adlandirilamaz.";
	static final String ADMIN_ROLE_DELETE = "Admin rolu silinemez.";

	private static final String MODEL = "Spatie\\Permission\\Models\\Role";

	private final RoleRepository roles;
	private final PermissionRepository permissions;
	private final Assignments assignments;

	RoleService(RoleRepository roles, PermissionRepository permissions, Assignments assignments) {
		this.roles = roles;
		this.permissions = permissions;
		this.assignments = assignments;
	}

	@Transactional(readOnly = true)
	List<Role> all() {
		return roles.findAllByOrderByNameAsc();
	}

	@Transactional(readOnly = true)
	Role get(long id) {
		return roles.findById(id).orElseThrow(() -> new ModelNotFoundException(MODEL, id));
	}

	@Transactional
	Role create(CreateRoleRequest request) {
		List<String> names = request.permissions() == null ? List.of() : request.permissions();
		ValidationErrors errors = new ValidationErrors();
		if (roles.existsByName(request.name())) {
			errors.add("name", LaravelMessages.UNIQUE);
		}
		checkPermissions(errors, names);
		errors.throwIfAny();
		Role role = new Role();
		role.setName(request.name());
		role.setGuardName(Role.GUARD);
		role.setCreatedAt(Timestamps.now());
		role.setUpdatedAt(role.getCreatedAt());
		roles.save(role);
		assignments.syncRolePermissions(role.getId(), permissionIds(names));
		return role;
	}

	@Transactional
	Role update(long id, UpdateRoleRequest request) {
		Role role = get(id);
		List<String> names = request.permissions() == null ? List.of() : request.permissions();
		ValidationErrors errors = new ValidationErrors();
		if (roles.existsByNameAndIdNot(request.name(), id)) {
			errors.add("name", LaravelMessages.UNIQUE);
		}
		checkPermissions(errors, names);
		errors.throwIfAny();
		if (Role.ADMIN.equals(role.getName()) && !Role.ADMIN.equals(request.name())) {
			throw HttpStatusException.unprocessable(ADMIN_ROLE_RENAME);
		}
		if (!Objects.equals(role.getName(), request.name())) {
			role.setName(request.name());
			role.setUpdatedAt(Timestamps.now());
		}
		assignments.syncRolePermissions(role.getId(), permissionIds(names));
		return role;
	}

	@Transactional
	void delete(long id) {
		Role role = get(id);
		if (Role.ADMIN.equals(role.getName())) {
			throw HttpStatusException.unprocessable(ADMIN_ROLE_DELETE);
		}
		// model_has_roles ve role_has_permissions FK ile (ON DELETE CASCADE) silinir
		roles.delete(role);
		assignments.forgetLaravelPermissionCache();
	}

	private void checkPermissions(ValidationErrors errors, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			String path = "permissions." + i;
			if (names.get(i) == null) {
				errors.add(path, LaravelMessages.STRING);
			}
			else if (!permissions.existsByName(names.get(i))) {
				errors.add(path, LaravelMessages.EXISTS);
			}
		}
	}

	private List<Long> permissionIds(List<String> names) {
		Map<String, Long> byName = permissions.findByNameInAndGuardName(names, Role.GUARD).stream()
			.collect(Collectors.toMap(Permission::getName, Permission::getId, (a, b) -> a));
		return names.stream().map(name -> require(byName, name)).toList();
	}

	/** Dogrulama guard'a bakmaz; baska guard'daki izin atanamaz, bu bir veri hatasidir. */
	private static Long require(Map<String, Long> byName, String name) {
		Long id = byName.get(name);
		if (id == null) {
			throw new IllegalStateException("There is no permission named `" + name + "` for guard `" + Role.GUARD + "`.");
		}
		return id;
	}

}
