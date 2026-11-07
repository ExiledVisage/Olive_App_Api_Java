package com.olivamore.api.identity;

import com.olivamore.api.identity.domain.Assignments;
import com.olivamore.api.identity.domain.Role;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Laravel {@code $user->can('izin')}: admin rolu her izne sahip ({@code Gate::before}), digerleri dogrudan ya da
 * rol uzerinden. Rota seviyesinde ayrilamayan (ayni endpointten gecen) yetkiler icin.
 */
@Component
public class Permissions {

	private final Assignments assignments;

	Permissions(Assignments assignments) {
		this.assignments = assignments;
	}

	public boolean can(CurrentUser user, String permission) {
		if (user == null) {
			return false;
		}
		if (assignments.hasAnyRole(user.id(), List.of(Role.ADMIN))) {
			return true;
		}
		return assignments.permissionNames(user.id()).contains(permission);
	}

}
