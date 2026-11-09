package com.olivamore.api.inventory.material;

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

/** Materyal; miktari kolon olcegine (4 basamak) cevrilerek yazilir. */
@Entity
@Table(name = "materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long productionBatchId;

	@Column(nullable = false)
	private Long productionProductTypeId;

	private Long parentMaterialId;

	@Column(nullable = false)
	@Convert(converter = MaterialOrigin.OriginConverter.class)
	private MaterialOrigin originType;

	@Column(nullable = false)
	private BigDecimal quantity;

	@Column(nullable = false)
	@Convert(converter = MaterialUnit.UnitConverter.class)
	private MaterialUnit unit;

	@Column(nullable = false)
	@Convert(converter = MaterialStatus.StatusConverter.class)
	private MaterialStatus status = MaterialStatus.ACTIVE;

	@Convert(converter = LocationType.LocationConverter.class)
	private LocationType currentLocationType;

	private Long currentLocationId;

	private Long sourceProductionStageId;

	private Long createdByBatchProcessId;

	private Long closedByBatchProcessId;

	private String note;

	/** Tanka alinacak yeni materyal: once konumsuz yaratilir. */
	public Material(Long productionBatchId, long productionProductTypeId, MaterialOrigin originType,
		BigDecimal quantity, MaterialUnit unit, Long sourceProductionStageId, Long createdByBatchProcessId,
		String note) {
		this.productionBatchId = productionBatchId;
		this.productionProductTypeId = productionProductTypeId;
		this.originType = originType;
		this.quantity = Quantities.material(quantity);
		this.unit = unit;
		this.sourceProductionStageId = sourceProductionStageId;
		this.createdByBatchProcessId = createdByBatchProcessId;
		this.note = note;
	}

	/** Bu materyalden ayrilan parca: ayni ozellikler, ayni konum, proses verilirse prosese bagli. */
	public Material splitOff(BigDecimal portion, Long batchProcessId) {
		Material child = new Material();
		child.productionBatchId = productionBatchId;
		child.productionProductTypeId = productionProductTypeId;
		child.parentMaterialId = id;
		child.originType = originType;
		child.quantity = Quantities.material(portion);
		child.unit = unit;
		child.currentLocationType = currentLocationType;
		child.currentLocationId = currentLocationId;
		child.sourceProductionStageId = sourceProductionStageId;
		child.createdByBatchProcessId = batchProcessId != null ? batchProcessId : createdByBatchProcessId;
		child.note = note;
		return child;
	}

	public void reduceTo(BigDecimal remaining) {
		quantity = Quantities.material(remaining);
	}

	public void moveToStorageUnit(long unitId) {
		currentLocationType = LocationType.STORAGE_UNIT;
		currentLocationId = unitId;
	}

	/** Tanktan cikan miktar kapanir: konum silinir; proses verilirse kapatan proses o olur. */
	public void close(MaterialStatus closedStatus, Long batchProcessId) {
		status = closedStatus;
		currentLocationType = null;
		currentLocationId = null;
		if (batchProcessId != null) {
			closedByBatchProcessId = batchProcessId;
		}
	}

}
