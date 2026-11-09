package com.olivamore.api.productionline.stage;

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

@Entity
@Table(name = "production_stages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductionStage extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionLineId;

	private Long processTypeId;

	private String name;

	@Column(nullable = false)
	private int stageOrder;

	@Column(name = "is_required", nullable = false)
	private boolean required = true;

	@Column(nullable = false)
	private boolean requiresApproval;

	ProductionStage(long lineId, int order, CreateProductionStageRequest request) {
		productionLineId = lineId;
		stageOrder = order;
		processTypeId = request.processTypeId();
		name = request.name();
		if (request.isRequired() != null) {
			required = request.isRequired();
		}
		if (request.requiresApproval() != null) {
			requiresApproval = request.requiresApproval();
		}
	}

	void moveTo(int order) {
		stageOrder = order;
	}

	/** Gelmeyen alanlar degismez; sira {@link #moveTo} ile. */
	void apply(UpdateProductionStageRequest request) {
		Patch.ifPresent(request.processTypeId(), value -> processTypeId = value);
		if (request.name() != null) {
			name = request.name();
		}
		if (request.isRequired() != null) {
			required = request.isRequired();
		}
		if (request.requiresApproval() != null) {
			requiresApproval = request.requiresApproval();
		}
	}

}
