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

	/** Bunun altinda kalan girdi tukenmis sayilir. */
	private static final BigDecimal CONSUMED_BELOW = new BigDecimal("0.000001");

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

	/** Uretim prosesinin yarattigi materyal: partinin icinde durur. */
	public static Material ofBatchProcess(long batchId, long typeId, Long parentId, BigDecimal quantity, MaterialUnit unit,
		long stageId, long processId, String note) {
		Material material = new Material(batchId, typeId, MaterialOrigin.BATCH_PROCESS, quantity, unit, stageId, processId,
			note);
		material.parentMaterialId = parentId;
		material.currentLocationType = LocationType.PRODUCTION_BATCH;
		material.currentLocationId = batchId;
		return material;
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

	/** Tartimda olculen miktar; konumu yoksa partiye yerlesir, proses notu varsa yazilir. */
	public void recordWeighing(BigDecimal measured, MaterialUnit measuredUnit, long stageId, long batchId,
		String processNote) {
		quantity = Quantities.material(measured);
		unit = measuredUnit;
		sourceProductionStageId = stageId;
		if (currentLocationType == null) {
			currentLocationType = LocationType.PRODUCTION_BATCH;
		}
		if (currentLocationId == null) {
			currentLocationId = batchId;
		}
		if (processNote != null) {
			note = processNote;
		}
	}

	/** Donusum girdisi: kalan yoksa proses tarafindan tuketilir (konum silinmez), varsa miktar azalir. */
	public void consumeInput(BigDecimal used, long processId, String processNote) {
		BigDecimal remaining = quantity.subtract(used);
		if (remaining.compareTo(CONSUMED_BELOW) <= 0) {
			status = MaterialStatus.CONSUMED;
			closedByBatchProcessId = processId;
		}
		else {
			quantity = Quantities.material(remaining);
		}
		if (processNote != null) {
			note = processNote;
		}
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
