package com.olivamore.api.finishedproduct.storage;

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

/** Depodaki bir urunun toplam adedi. */
@Entity
@Table(name = "finished_product_storage_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StorageStock extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_storage_id", nullable = false)
	private Long storageId;

	@Column(nullable = false)
	private Long finishedProductId;

	@Column(nullable = false)
	private int quantity;

	StorageStock(long storageId, long finishedProductId) {
		this.storageId = storageId;
		this.finishedProductId = finishedProductId;
	}

	void add(long amount) {
		quantity += (int) amount;
	}

}
