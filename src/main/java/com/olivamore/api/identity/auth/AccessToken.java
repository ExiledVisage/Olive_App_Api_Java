package com.olivamore.api.identity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code personal_access_tokens} (Laravel Sanctum). */
@Entity
@Table(name = "personal_access_tokens")
@Getter
@Setter
public class AccessToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tokenable_type", nullable = false)
	private String tokenableType;

	@Column(name = "tokenable_id", nullable = false)
	private Long tokenableId;

	@Column(nullable = false, columnDefinition = "text")
	private String name;

	/** Duz token'in SHA-256 hex ozeti (64 karakter). */
	@Column(nullable = false, length = 64)
	private String token;

	@Column(columnDefinition = "text")
	private String abilities;

	@Column(name = "last_used_at")
	private LocalDateTime lastUsedAt;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
