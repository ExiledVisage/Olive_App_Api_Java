package com.olivamore.api.identity.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	boolean existsByIdAndUserIdAndRevokedAtIsNull(UUID id, long userId);

}
