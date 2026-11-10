package com.olivamore.api.identity.role;

import java.util.List;

record RoleResponse(Long id, String name, List<String> permissions) {
}
