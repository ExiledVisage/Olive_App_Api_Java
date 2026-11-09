package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Hattin girdi ya da cikti urunu. */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
abstract class LineProduct extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionLineId;

	@Column(nullable = false)
	private Long productionProductId;

	private Long productionProductTypeId;

	@Column(nullable = false)
	private int sortOrder;

	LineProduct(long lineId, LineProductRequest request, int defaultOrder) {
		productionLineId = lineId;
		productionProductId = request.productionProductId();
		productionProductTypeId = request.productionProductTypeId();
		sortOrder = request.sortOrder() != null ? request.sortOrder() : defaultOrder;
	}

}
