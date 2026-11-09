package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.lot.LotEnums.MovementType;
import com.olivamore.api.packaging.lot.LotEnums.Source;
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

/** Lot hareket defteri. */
@Entity
@Table(name = "packaging_lot_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PackagingLotMovement extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long packagingLotId;

	@Column(nullable = false)
	private Long packagingItemId;

	private Long finishedProductRunId;

	private Long userId;

	@Column(nullable = false)
	@Convert(converter = LotEnums.SourceConverter.class)
	private Source sourceType;

	@Column(nullable = false)
	@Convert(converter = LotEnums.MovementTypeConverter.class)
	private MovementType movementType;

	@Column(nullable = false)
	private int quantity;

	private String note;

	@Column(nullable = false)
	private LocalDateTime movedAt;

	PackagingLotMovement(PackagingLot lot, MovementType type, Source source, int quantity, String note,
		LocalDateTime movedAt, long userId, Long runId) {
		packagingLotId = lot.getId();
		packagingItemId = lot.getItem().getId();
		finishedProductRunId = runId;
		this.userId = userId;
		sourceType = source;
		movementType = type;
		this.quantity = quantity;
		this.note = note;
		this.movedAt = movedAt;
	}

}
