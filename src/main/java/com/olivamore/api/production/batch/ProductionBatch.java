package com.olivamore.api.production.batch;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** Uretim partisi; {@code quantity_kg} kolon olcegine (4 basamak) cevrilerek yazilir. */
@Entity
@Table(name = "production_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionBatch extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String batchCode;

	@Column(nullable = false)
	private Long productionLineId;

	private Long supplierId;

	private Long supplierFieldId;

	private Long createdByUserId;

	@Column(nullable = false)
	@Convert(converter = BatchStatus.StatusConverter.class)
	private BatchStatus status = BatchStatus.PENDING;

	private BigDecimal quantityKg;

	private LocalDateTime receivedAt;

	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	private String note;

	ProductionBatch(String batchCode, long productionLineId, Long supplierId, Long supplierFieldId, long createdByUserId,
		BatchStatus status, BigDecimal quantityKg, LocalDateTime receivedAt, LocalDateTime startedAt,
		LocalDateTime completedAt, String note) {
		this.batchCode = batchCode;
		this.productionLineId = productionLineId;
		this.supplierId = supplierId;
		this.supplierFieldId = supplierFieldId;
		this.createdByUserId = createdByUserId;
		this.status = status;
		this.quantityKg = scaled(quantityKg);
		this.receivedAt = receivedAt;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.note = note;
	}

	void changeSupplier(Long supplier, Long field) {
		supplierId = supplier;
		supplierFieldId = field;
	}

	void changeBatchCode(String value) {
		batchCode = value;
	}

	void changeStatus(BatchStatus value) {
		status = value;
	}

	void changeQuantityKg(BigDecimal value) {
		quantityKg = scaled(value);
	}

	void changeReceivedAt(LocalDateTime value) {
		receivedAt = value;
	}

	void changeStartedAt(LocalDateTime value) {
		startedAt = value;
	}

	void changeCompletedAt(LocalDateTime value) {
		completedAt = value;
	}

	void changeNote(String value) {
		note = value;
	}

	public void markInProgress(LocalDateTime now) {
		status = BatchStatus.IN_PROGRESS;
		started(now);
		completedAt = null;
	}

	public void markCompleted(LocalDateTime now) {
		status = BatchStatus.COMPLETED;
		started(now);
		if (completedAt == null) {
			completedAt = now;
		}
	}

	public void markPending() {
		status = BatchStatus.PENDING;
		startedAt = null;
		completedAt = null;
	}

	private void started(LocalDateTime now) {
		if (startedAt == null) {
			startedAt = now;
		}
	}

	private static BigDecimal scaled(BigDecimal value) {
		return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
	}

}
