package com.olivamore.api.identity.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/** Izinler seed'den gelir, API'den degismez. */
@Entity
@Table(name = "permissions")
@Getter
public class Permission {

	@Id
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
