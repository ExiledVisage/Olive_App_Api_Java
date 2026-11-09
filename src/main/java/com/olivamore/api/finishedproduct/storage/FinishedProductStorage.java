package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bitmis urun deposu. */
@Entity
@Table(name = "finished_product_storages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class FinishedProductStorage extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	FinishedProductStorage(CreateStorageRequest request) {
		name = request.name();
		description = request.description();
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateStorageRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		Patch.ifPresent(request.description(), value -> description = value);
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
