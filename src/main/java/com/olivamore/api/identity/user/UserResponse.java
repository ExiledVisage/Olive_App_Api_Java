package com.olivamore.api.identity.user;

import java.time.LocalDateTime;
import java.util.List;

/** Kullanici, rolleri ve butun izinleri (dogrudan + rollerden). */
public record UserResponse(Long id, String name, String email, List<String> roles, List<String> permissions,
	LocalDateTime createdAt) {
}
