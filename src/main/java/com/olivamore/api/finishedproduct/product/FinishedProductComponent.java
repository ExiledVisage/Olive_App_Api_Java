package com.olivamore.api.finishedproduct.product;

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

/** Urunun bir ambalaj bileseni. */
@Entity
@Table(name = "finished_product_components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinishedProductComponent extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long finishedProductId;

	@Column(nullable = false)
	private Long packagingItemId;

	@Column(nullable = false)
	private int quantity;

	FinishedProductComponent(long productId, long packagingItemId, int quantity) {
		finishedProductId = productId;
		this.packagingItemId = packagingItemId;
		this.quantity = quantity;
	}

}
