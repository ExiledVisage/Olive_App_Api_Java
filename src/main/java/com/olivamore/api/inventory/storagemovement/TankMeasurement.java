package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.Quantities;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Son tank analizi: o anki hesaplanan deger, olculen deger ve fark (sapma); degerler 4 basamak. */
@Entity
@Table(name = "tank_measurements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TankMeasurement extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long storageUnitId;

	@Column(nullable = false)
	private Long materialId;

	@Column(name = "production_product_attribute_definition_id", nullable = false)
	private Long definitionId;

	private BigDecimal calculatedValue;

	@Column(nullable = false)
	private BigDecimal measuredValue;

	private BigDecimal difference;

	private Long measuredByUserId;

	private String note;

	@Column(nullable = false)
	private LocalDateTime measuredAt;

	/** @param calculated olcumden onceki deger; yoksa null (fark da null) */
	TankMeasurement(long storageUnitId, long materialId, long definitionId, BigDecimal calculated,
		BigDecimal measured, Long measuredByUserId, String note, LocalDateTime measuredAt) {
		this.storageUnitId = storageUnitId;
		this.materialId = materialId;
		this.definitionId = definitionId;
		this.calculatedValue = Quantities.material(calculated);
		this.measuredValue = Quantities.material(measured);
		this.difference = calculated == null ? null : Quantities.material(measured.subtract(calculated));
		this.measuredByUserId = measuredByUserId;
		this.note = note;
		this.measuredAt = measuredAt;
	}

}
