package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.item.PackagingItem;
import com.olivamore.api.packaging.lot.LotEnums.Status;
import com.olivamore.api.packaging.supplier.ProductSupplier;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "packaging_lots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PackagingLot extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "packaging_item_id")
	private PackagingItem item;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_supplier_id")
	private ProductSupplier supplier;

	@Column(nullable = false)
	private String lotCode;

	@Column(nullable = false)
	private LocalDateTime receivedAt;

	@Column(nullable = false)
	private int quantityReceived;

	@Column(nullable = false)
	private int quantityRemaining;

	@Column(nullable = false)
	@Convert(converter = LotEnums.StatusConverter.class)
	private Status status;

	private String note;

	PackagingLot(PackagingItem item, ProductSupplier supplier, String lotCode, int quantity,
		LocalDateTime receivedAt, String note) {
		this.item = item;
		this.supplier = supplier;
		this.lotCode = lotCode;
		this.receivedAt = receivedAt;
		quantityReceived = quantity;
		quantityRemaining = quantity;
		status = Status.ACTIVE;
		this.note = note;
	}

	/** Kunye duzeltmesi: miktar yalnizca hareketle degisir. */
	void apply(UpdatePackagingLotRequest request) {
		if (request.lotCode() != null) {
			lotCode = request.lotCode();
		}
		if (request.receivedAt() != null) {
			receivedAt = request.receivedAt();
		}
		Patch.ifPresent(request.note(), value -> note = value);
	}

	void assignSupplier(ProductSupplier value) {
		supplier = value;
	}

	void changeRemaining(int remaining) {
		quantityRemaining = remaining;
		status = Status.forRemaining(remaining);
	}

	boolean untouched() {
		return quantityRemaining == quantityReceived;
	}

}
