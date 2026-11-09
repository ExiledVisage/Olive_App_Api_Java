package com.olivamore.api.process.processtype;

import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "process_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProcessType extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(nullable = false)
	@Convert(converter = ProcessCategoryConverter.class)
	private ProcessCategory category;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	ProcessType(CreateProcessTypeRequest request) {
		name = request.name();
		description = request.description();
		category = request.category();
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateProcessTypeRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		Patch.ifPresent(request.description(), value -> description = value);
		if (request.category() != null) {
			category = request.category();
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
