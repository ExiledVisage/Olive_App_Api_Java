package com.olivamore.api.inventory.storageunit;

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

/** Tanka girebilecek urun kategorisi. */
@Entity
@Table(name = "storage_unit_allowed_production_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StorageUnitAllowedProduct extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long storageUnitId;

	@Column(nullable = false)
	private Long productionProductId;

	StorageUnitAllowedProduct(long storageUnitId, long productionProductId) {
		this.storageUnitId = storageUnitId;
		this.productionProductId = productionProductId;
	}

}
