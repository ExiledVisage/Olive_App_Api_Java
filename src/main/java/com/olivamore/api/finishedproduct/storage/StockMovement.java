package com.olivamore.api.finishedproduct.storage;

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

import java.time.LocalDateTime;
import java.util.UUID;

/** Raf hareketi defteri. */
@Entity
@Table(name = "finished_product_stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StockMovement extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_storage_id", nullable = false)
	private Long storageId;

	@Column(name = "finished_product_storage_slot_id", nullable = false)
	private Long slotId;

	@Column(nullable = false)
	private Long finishedProductId;

	private Long finishedProductRunId;

	private Long orderId;

	@Column(nullable = false)
	@Convert(converter = MovementType.TypeConverter.class)
	private MovementType movementType;

	@Column(nullable = false)
	private int quantity;

	private Long counterpartySlotId;

	private UUID transferReference;

	private Long createdByUserId;

	private String note;

	@Column(nullable = false)
	private LocalDateTime movedAt;

	StockMovement(long storageId, long slotId, long productId, MovementType type, long quantity,
		Long actorId, String note,
		Long counterpartySlotId, UUID reference, Long runId, Long orderId, LocalDateTime movedAt) {
		this.storageId = storageId;
		this.slotId = slotId;
		finishedProductId = productId;
		movementType = type;
		this.quantity = (int) quantity;
		createdByUserId = actorId;
		this.note = note;
		this.counterpartySlotId = counterpartySlotId;
		transferReference = reference;
		finishedProductRunId = runId;
		this.orderId = orderId;
		this.movedAt = movedAt;
	}

}
