package com.olivamore.api.identity.user;

import com.olivamore.api.identity.auth.AuthService;
import com.olivamore.api.identity.role.Role;
import com.olivamore.api.identity.role.RoleRepository;
import com.olivamore.api.identity.security.Assignments;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Kullanicilar ve rolleri. Admin kullanici silinemez, admin rolu ondan kaldirilamaz. */
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

	@Transactional(readOnly = true)
	List<User> all() {
		return users.latest();
	}

	@Transactional(readOnly = true)
	User get(long id) {
		return users.findById(id).orElseThrow(() -> new ModelNotFoundException("User", id));
	}

	@Transactional
	User create(CreateUserRequest request) {
		List<String> names = request.roles() == null ? List.of() : request.roles();
		ValidationErrors errors = new ValidationErrors();
		if (users.existsByEmail(request.email())) {
			errors.add("email", LaravelMessages.UNIQUE);
		}
		checkRoles(errors, names);
		errors.throwIfAny();
		User user = new User();
		user.setName(request.name());
		user.setEmail(request.email());
		user.setPassword(AuthService.hash(request.password()));
		user.setCreatedAt(Timestamps.now());
		user.setUpdatedAt(user.getCreatedAt());
		user.replaceRoles(roles.findByNameIn(names));
		users.save(user);
		return user;
	}

	/** Bir sey degismediyse {@code updated_at} da degismez. */
	@Transactional
	User update(long id, UpdateUserRequest request) {
		User user = get(id);
		ValidationErrors errors = new ValidationErrors();
		if (users.existsByEmailAndIdNot(request.email(), id)) {
			errors.add("email", LaravelMessages.UNIQUE);
		}
		if (request.roles() != null && request.roles().value() != null) {
			checkRoles(errors, request.roles().value());
		}
		errors.throwIfAny();
		boolean dirty = !Objects.equals(user.getName(), request.name())
			|| !Objects.equals(user.getEmail(), request.email());
		user.setName(request.name());
		user.setEmail(request.email());
		if (request.password() != null) {
			user.setPassword(AuthService.hash(request.password()));
			dirty = true;
		}
		if (dirty) {
			user.setUpdatedAt(Timestamps.now());
		}
		Patch.ifPresent(request.roles(), names -> syncRoles(user, names == null ? List.of() : names));
		return user;
	}

	@Transactional
	User updateRoles(long id, UpdateUserRolesRequest request) {
		User user = get(id);
		ValidationErrors errors = new ValidationErrors();
		checkRoles(errors, request.roles());
		errors.throwIfAny();
		syncRoles(user, request.roles());
		return user;
	}

	@Transactional
	void delete(long id) {
		User user = get(id);
		if (assignments.hasAnyRole(user.getId(), List.of(Role.ADMIN))) {
			throw HttpStatusException.unprocessable(ADMIN_USER_DELETE);
		}
		users.delete(user);
	}

	private void syncRoles(User user, List<String> names) {
		if (assignments.hasAnyRole(user.getId(), List.of(Role.ADMIN)) && !names.contains(Role.ADMIN)) {
			throw HttpStatusException.unprocessable(ADMIN_ROLE_REMOVAL);
		}
		user.replaceRoles(roles.findByNameIn(names));
	}

	private void checkRoles(ValidationErrors errors, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			String path = "roles." + i;
			if (names.get(i) == null) {
				errors.add(path, LaravelMessages.STRING);
			}
			else if (!roles.existsByName(names.get(i))) {
				errors.add(path, LaravelMessages.EXISTS);
			}
		}
	}

}
