package com.olivamore.api.identity.security;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.identity.RequiresRole;
import com.olivamore.api.identity.security.Assignments;
import com.olivamore.api.identity.role.Role;
import com.olivamore.api.shared.error.HttpStatusException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Spatie'nin {@code role:} ve {@code permission:} middleware'leri. Controller metodu cagrilmadan hemen
 * once calisir, yani parametreler (model binding, 404) cozulduktan sonra: Laravel'deki sira
 * 401 (auth) -> 404 (binding) -> 403 (rol/izin).
 * <ul>
 *   <li>rol: kullanici rollerden birine sahip olmali (admin icin istisna yok)</li>
 *   <li>izin: admin her izne sahip ({@code Gate::before}); digerleri dogrudan ya da rol uzerinden</li>
 * </ul>
 */
@Aspect
@Component
class AccessAspect {

	static final String NOT_LOGGED_IN = "User is not logged in.";
	static final String WRONG_ROLES = "User does not have the right roles.";
	static final String WRONG_PERMISSIONS = "User does not have the right permissions.";

	private final Assignments assignments;

	AccessAspect(Assignments assignments) {
		this.assignments = assignments;
	}

	@Before("@within(com.olivamore.api.identity.RequiresRole) || @annotation(com.olivamore.api.identity.RequiresRole)"
		+ " || @within(com.olivamore.api.identity.RequiresPermission) || @annotation(com.olivamore.api.identity.RequiresPermission)")
	void check(JoinPoint joinPoint) {
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
		Class<?> type = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : method.getDeclaringClass();

		// Laravel: route grubunun middleware'i once, route'unki sonra
		checkRole(AnnotatedElementUtils.findMergedAnnotation(type, RequiresRole.class));
		checkPermission(AnnotatedElementUtils.findMergedAnnotation(type, RequiresPermission.class));
		checkRole(AnnotatedElementUtils.findMergedAnnotation(method, RequiresRole.class));
		checkPermission(AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class));
	}

	private void checkRole(RequiresRole required) {
		if (required == null) {
			return;
		}
		CurrentUser user = user();
		if (!assignments.hasAnyRole(user.id(), List.of(required.value()))) {
			throw HttpStatusException.forbidden(WRONG_ROLES);
		}
	}

	private void checkPermission(RequiresPermission required) {
		if (required == null) {
			return;
		}
		CurrentUser user = user();
		if (assignments.hasAnyRole(user.id(), List.of(Role.ADMIN))) {
			return;
		}
		List<String> permissions = assignments.permissionNames(user.id());
		for (String permission : required.value()) {
			if (permissions.contains(permission)) {
				return;
			}
		}
		throw HttpStatusException.forbidden(WRONG_PERMISSIONS);
	}

	private static CurrentUser user() {
		CurrentUser user = Access.current();
		if (user == null) {
			throw HttpStatusException.forbidden(NOT_LOGGED_IN);
		}
		return user;
	}

}
