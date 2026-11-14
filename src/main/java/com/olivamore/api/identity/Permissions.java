package com.olivamore.api.identity;

import com.olivamore.api.identity.security.Assignments;
import com.olivamore.api.identity.role.Role;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kodun icinden izin kontrolu: admin rolu her izne sahip, digerleri izne dogrudan ya da rol uzerinden sahip olur.
 * Rota seviyesinde ayrilamayan (ayni endpointten gecen) yetkiler icin.
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
