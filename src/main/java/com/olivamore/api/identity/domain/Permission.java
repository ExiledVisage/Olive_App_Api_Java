package com.olivamore.api.identity.domain;

import com.olivamore.api.shared.web.binding.LaravelModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/** {@code permissions} (Spatie {@code Permission}). Izinler seeder'dan gelir, API'den degismez. */
@Entity
@Table(name = "permissions")
@LaravelModel("Spatie\\Permission\\Models\\Permission")
@Getter
public class Permission {

	@Id
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
