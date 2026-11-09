package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Uzantinin girdi ya da cikti urunu. */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
abstract class ExtensionProduct extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionStageExtensionId;

	@Column(nullable = false)
	private Long productionProductId;

	@Column(nullable = false)
	private int sortOrder;

	@Column(name = "is_required", nullable = false)
	private boolean required = true;

	ExtensionProduct(long extensionId, long productId, int order) {
		productionStageExtensionId = extensionId;
		productionProductId = productId;
		sortOrder = order;
	}

}
