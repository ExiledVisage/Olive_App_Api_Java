package com.olivamore.api.identity.permission;

record PermissionResponse(Long id, String name) {

	static PermissionResponse of(Permission permission) {
		return new PermissionResponse(permission.getId(), permission.getName());
	}

}
