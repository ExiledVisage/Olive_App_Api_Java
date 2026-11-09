package com.olivamore.api.productionline.stageextension;

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

@Entity
@Table(name = "production_stage_extensions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductionStageExtension extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionStageId;

	@Column(nullable = false)
	private Long stageExtensionTypeId;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(name = "is_enabled", nullable = false)
	private boolean enabled = true;

	@Column(nullable = false)
	private int sortOrder;

	ProductionStageExtension(long stageId, StageExtensionRequest request, int defaultOrder) {
		productionStageId = stageId;
		stageExtensionTypeId = request.stageExtensionTypeId();
		required = Boolean.TRUE.equals(request.isRequired());
		if (request.isEnabled() != null) {
			enabled = request.isEnabled();
		}
		sortOrder = request.sortOrder() != null ? request.sortOrder() : defaultOrder;
	}

}
