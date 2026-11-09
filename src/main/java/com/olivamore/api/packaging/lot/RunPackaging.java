package com.olivamore.api.packaging.lot;

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

/** Dolum kosusuna tahsis edilen lot miktari. */
@Entity
@Table(name = "finished_product_run_packagings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RunPackaging extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long finishedProductRunId;

	@Column(nullable = false)
	private Long packagingItemId;

	@Column(nullable = false)
	private Long packagingLotId;

	@Column(nullable = false)
	private int quantity;

	RunPackaging(long runId, long itemId, long lotId, int quantity) {
		finishedProductRunId = runId;
		packagingItemId = itemId;
		packagingLotId = lotId;
		this.quantity = quantity;
	}

}
