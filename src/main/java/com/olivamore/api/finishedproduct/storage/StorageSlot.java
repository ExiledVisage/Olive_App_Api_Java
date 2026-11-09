package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.finishedproduct.support.Quantities;
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

/** Depodaki raf: hacim, kapasite, istege bagli urun kilidi. */
@Entity
@Table(name = "finished_product_storage_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StorageSlot extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_storage_id", nullable = false)
	private Long storageId;

	private String name;

	@Column(nullable = false)
	private BigDecimal volumeLitre;

	@Column(nullable = false)
	private int maxQuantity;

	@Column(nullable = false)
	private int currentQuantity;

	private Long finishedProductId;

	StorageSlot(long storageId, CreateSlotRequest request) {
		this.storageId = storageId;
		name = request.name();
		volumeLitre = Quantities.normalize(request.volumeLitre());
		maxQuantity = request.maxQuantity();
		finishedProductId = request.finishedProductId();
	}

	void changeName(String value) {
		name = value;
	}

	void changeVolume(BigDecimal value) {
		volumeLitre = Quantities.normalize(value);
	}

	void changeCapacity(int value) {
		maxQuantity = value;
	}

	void lockTo(Long productId) {
		finishedProductId = productId;
	}

	void add(long quantity) {
		currentQuantity += (int) quantity;
	}

	void remove(long quantity) {
		currentQuantity -= (int) quantity;
	}

	int available() {
		return maxQuantity - currentQuantity;
	}

}
