package com.olivamore.api.identity.user;

import com.olivamore.api.shared.web.binding.LaravelModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code users} (Laravel {@code App\Models\User}). */
@Entity
@Table(name = "users")
@LaravelModel("User")
@Getter
@Setter
public class User {

	/** Spatie pivot tablolarindaki {@code model_type}, Sanctum'daki {@code tokenable_type}. */
	public static final String MORPH_TYPE = "App\\Models\\User";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column(name = "email_verified_at")
	private LocalDateTime emailVerifiedAt;

	@Column(nullable = false)
	private String password;

	@Column(name = "remember_token", length = 100)
	private String rememberToken;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
