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

/**
 * Uretim partisi; {@code quantity_kg} kolon olcegine (4 basamak) cevrilerek yazilir. {@code parent_batch_id} doluysa
 * parti bir teslimatin grubudur: teslimat makine oncesi asamalari yapar, grup makineli asamalari.
 */
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

	private Long parentBatchId;

	@Column(nullable = false)
	private Long productionLineId;

	private Long supplierId;

	private Long supplierFieldId;

	private Long createdByUserId;

	@Column(nullable = false)
	@Convert(converter = BatchStatus.StatusConverter.class)
	private BatchStatus status = BatchStatus.PENDING;

	private BigDecimal quantityKg;

	/** Teslimatin kamyon plakasi; kantar fisinin numarasi istege bagli. */
	private String truckPlate;

	private String weighingSlipNo;

	private LocalDateTime receivedAt;

	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	private String note;

	/** Alim bilgisi yalnizca teslimatta; fiyat KDV haric TL/kg. */
	@Convert(converter = SupplyChannel.ChannelConverter.class)
	private SupplyChannel supplyChannel;

	private BigDecimal pricePerKg;

	private BigDecimal vatRate;

	private BigDecimal transportCost;

	private String priceNote;

	ProductionBatch(String batchCode, long productionLineId, Long supplierId, Long supplierFieldId, long createdByUserId,
		BatchStatus status, BigDecimal quantityKg, String truckPlate, String weighingSlipNo, LocalDateTime receivedAt,
		LocalDateTime startedAt, LocalDateTime completedAt, String note) {
		this.batchCode = batchCode;
		this.productionLineId = productionLineId;
		this.supplierId = supplierId;
		this.supplierFieldId = supplierFieldId;
		this.createdByUserId = createdByUserId;
		this.status = status;
		this.quantityKg = scaled(quantityKg);
		this.truckPlate = truckPlate;
		this.weighingSlipNo = weighingSlipNo;
		this.receivedAt = receivedAt;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.note = note;
	}

	/** Teslimatin grubu: hat, tedarikci, tarla, plaka, fis ve varis teslimattan. */
	static ProductionBatch groupOf(ProductionBatch delivery, String code, BigDecimal quantityKg, long userId,
		String note) {
		ProductionBatch group = new ProductionBatch(code, delivery.productionLineId, delivery.supplierId,
			delivery.supplierFieldId, userId, BatchStatus.PENDING, quantityKg, delivery.truckPlate, delivery.weighingSlipNo,
			delivery.receivedAt, null, null, note);
		group.parentBatchId = delivery.id;
		return group;
	}

	public boolean isGroup() {
		return parentBatchId != null;
	}

	/** Teslimattan gelen bilgiler gruba yazilir. */
	void copyDeliveryInfo(ProductionBatch delivery) {
		supplierId = delivery.supplierId;
		supplierFieldId = delivery.supplierFieldId;
		truckPlate = delivery.truckPlate;
		weighingSlipNo = delivery.weighingSlipNo;
		receivedAt = delivery.receivedAt;
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

	/** Teslimatin kantar tartimi teslimat kilosu olur. */
	public void recordWeighedKg(BigDecimal value) {
		quantityKg = scaled(value);
	}

	void changeQuantityKg(BigDecimal value) {
		quantityKg = scaled(value);
	}

	void changeTruckPlate(String value) {
		truckPlate = value;
	}

	void changeWeighingSlipNo(String value) {
		weighingSlipNo = value;
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

	void changePurchase(SupplyChannel channel, BigDecimal price, BigDecimal vat, BigDecimal transport,
		String priceNote) {
		this.supplyChannel = channel;
		this.pricePerKg = price;
		this.vatRate = vat;
		this.transportCost = transport;
		this.priceNote = priceNote;
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
