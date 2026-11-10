package com.olivamore.api.production.batch;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Partinin giris ya da cikis urun tipi. */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
abstract class BatchProductType extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionBatchId;

	@Column(nullable = false)
	private Long productionProductTypeId;

	@Column(nullable = false)
	private int sortOrder;

	BatchProductType(long productionBatchId, long productionProductTypeId, int sortOrder) {
		this.productionBatchId = productionBatchId;
		this.productionProductTypeId = productionProductTypeId;
		this.sortOrder = sortOrder;
	}

}
