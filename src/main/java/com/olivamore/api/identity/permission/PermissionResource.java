package com.olivamore.api.identity.permission;

/** Laravel {@code PermissionResource}. */
final class PermissionResource {

	private PermissionResource() {
	}

	record Data(Long id, String name) {
	}

	static Data of(Permission permission) {
		return new Data(permission.getId(), permission.getName());
	}

}
