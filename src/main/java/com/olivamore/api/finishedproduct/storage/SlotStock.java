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

/** Raftaki bir urunun adedi. */
@Entity
@Table(name = "finished_product_storage_slot_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SlotStock extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_storage_slot_id", nullable = false)
	private Long slotId;

	@Column(nullable = false)
	private Long finishedProductId;

	@Column(nullable = false)
	private int quantity;

	SlotStock(long slotId, long finishedProductId) {
		this.slotId = slotId;
		this.finishedProductId = finishedProductId;
	}

	void add(long amount) {
		quantity += (int) amount;
	}

}
