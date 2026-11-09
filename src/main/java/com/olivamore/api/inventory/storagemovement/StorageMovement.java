package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialUnit;
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
import java.time.LocalDateTime;

/** Tank hareketi; miktari 2 basamak. */
@Entity
@Table(name = "storage_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageMovement extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long storageUnitId;

	@Column(nullable = false)
	private Long materialId;

	private Long batchProcessId;

	private Long finishedProductRunId;

	private Long counterpartyStorageUnitId;

	@Column(nullable = false)
	@Convert(converter = MovementSource.SourceConverter.class)
	private MovementSource sourceType;

	@Column(nullable = false)
	@Convert(converter = MovementType.TypeConverter.class)
	private MovementType movementType;

	private String transferReference;

	@Column(nullable = false)
	private BigDecimal quantity;

	@Column(nullable = false)
	@Convert(converter = MaterialUnit.UnitConverter.class)
	private MaterialUnit unit;

	private String note;

	private LocalDateTime movedAt;

	StorageMovement(long storageUnitId, long materialId, Long batchProcessId, Long finishedProductRunId,
		Long counterpartyStorageUnitId, MovementSource sourceType, MovementType movementType, String transferReference,
		BigDecimal quantity, MaterialUnit unit, String note, LocalDateTime movedAt) {
		this.storageUnitId = storageUnitId;
		this.materialId = materialId;
		this.batchProcessId = batchProcessId;
		this.finishedProductRunId = finishedProductRunId;
		this.counterpartyStorageUnitId = counterpartyStorageUnitId;
		this.sourceType = sourceType;
		this.movementType = movementType;
		this.transferReference = transferReference;
		this.quantity = quantity;
		this.unit = unit;
		this.note = note;
		this.movedAt = movedAt;
	}

}
