package com.olivamore.api.identity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Bir giris; access token'lar buna bagli, refresh token'lar bunun ailesi. Iptal edilince hepsi gecersiz olur. */
@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuthSession {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	AuthSession(UUID id, long userId, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.createdAt = createdAt;
	}

	boolean isRevoked() {
		return revokedAt != null;
	}

	void revoke(Instant at) {
		if (revokedAt == null) {
			revokedAt = at;
		}
	}

}
