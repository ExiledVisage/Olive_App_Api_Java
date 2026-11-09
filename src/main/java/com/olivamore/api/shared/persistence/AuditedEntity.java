package com.olivamore.api.shared.persistence;

import com.olivamore.api.shared.time.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/** {@code created_at} ve {@code updated_at} kolonlari olan entity'ler. Degisiklik yoksa updated_at degismez. */
@MappedSuperclass
@Getter
public abstract class AuditedEntity {

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = Timestamps.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = Timestamps.now();
	}

}
