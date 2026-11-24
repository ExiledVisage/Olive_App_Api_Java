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

/** Cetvelle seviye olcumu: okuma, geometriden hacim, kg, o anki defter miktari ve fark (tankin biriminde). */
@Entity
@Table(name = "tank_level_readings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TankLevelReading extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long storageUnitId;

	@Column(nullable = false)
	private BigDecimal measuredMm;

	@Column(nullable = false)
	private BigDecimal calculatedLitre;

	private BigDecimal calculatedKg;

	private BigDecimal densityKgPerLitre;

	@Column(nullable = false)
	private BigDecimal bookQuantity;

	@Column(nullable = false)
	@Convert(converter = MaterialUnit.UnitConverter.class)
	private MaterialUnit unit;

	private BigDecimal difference;

	private Long measuredByUserId;

	private String note;

	@Column(nullable = false)
	private LocalDateTime measuredAt;

	TankLevelReading(long storageUnitId, BigDecimal measuredMm, BigDecimal calculatedLitre, BigDecimal calculatedKg,
		BigDecimal density, BigDecimal bookQuantity, MaterialUnit unit, BigDecimal difference, long userId, String note,
		LocalDateTime measuredAt) {
		this.storageUnitId = storageUnitId;
		this.measuredMm = measuredMm;
		this.calculatedLitre = calculatedLitre;
		this.calculatedKg = calculatedKg;
		this.densityKgPerLitre = density;
		this.bookQuantity = bookQuantity;
		this.unit = unit;
		this.difference = difference;
		this.measuredByUserId = userId;
		this.note = note;
		this.measuredAt = measuredAt;
	}

}
