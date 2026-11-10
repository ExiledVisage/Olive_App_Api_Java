package com.olivamore.api.identity.role;

import com.olivamore.api.identity.permission.PermissionRepository;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Roller ve izinleri. Admin rolu yeniden adlandirilamaz ve silinemez. */
@Service
class RoleService {

	static final String ADMIN_ROLE_RENAME = "Admin rolu yeniden adlandirilamaz.";
	static final String ADMIN_ROLE_DELETE = "Admin rolu silinemez.";

	private static final String MODEL = "Role";

	private final RoleRepository roles;
	private final PermissionRepository permissions;

	RoleService(RoleRepository roles, PermissionRepository permissions) {
		this.roles = roles;
		this.permissions = permissions;
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
			errors.add("name", MessageKeys.UNIQUE);
		}
		checkPermissions(errors, names);
		errors.throwIfAny();
		Role role = new Role();
		role.setName(request.name());
		role.setCreatedAt(Timestamps.now());
		role.setUpdatedAt(role.getCreatedAt());
		role.replacePermissions(permissions.findByNameIn(names));
		roles.save(role);
		return role;
	}

	@Transactional
	Role update(long id, UpdateRoleRequest request) {
		Role role = get(id);
		List<String> names = request.permissions() == null ? List.of() : request.permissions();
		ValidationErrors errors = new ValidationErrors();
		if (roles.existsByNameAndIdNot(request.name(), id)) {
			errors.add("name", MessageKeys.UNIQUE);
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
		role.replacePermissions(permissions.findByNameIn(names));
		return role;
	}

	@Transactional
	void delete(long id) {
		Role role = get(id);
		if (Role.ADMIN.equals(role.getName())) {
			throw HttpStatusException.unprocessable(ADMIN_ROLE_DELETE);
		}
		roles.delete(role);
	}

	private void checkPermissions(ValidationErrors errors, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			String path = "permissions." + i;
			if (names.get(i) == null) {
				errors.add(path, MessageKeys.STRING);
			}
			else if (!permissions.existsByName(names.get(i))) {
				errors.add(path, MessageKeys.EXISTS);
			}
		}
	}

}
