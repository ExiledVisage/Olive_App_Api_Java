package com.olivamore.api.identity.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code roles} (Spatie {@code Role}). */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role {

	public static final String ADMIN = "admin";

	/**
	 * Laravel'deki butun roller ve izinler {@code web} guard'inda. (Laravel API'den olusan roller
	 * yanlislikla {@code sanctum} aliyordu; bkz. DEVIATIONS.md.)
	 */
	public static final String GUARD = "web";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "guard_name", nullable = false)
	private String guardName;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
