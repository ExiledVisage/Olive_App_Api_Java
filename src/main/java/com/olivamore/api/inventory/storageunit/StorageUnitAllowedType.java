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

/** Tanka girebilecek urun tipi. */
@Entity
@Table(name = "storage_unit_allowed_production_product_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StorageUnitAllowedType extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long storageUnitId;

	@Column(nullable = false)
	private Long productionProductTypeId;

	StorageUnitAllowedType(long storageUnitId, long productionProductTypeId) {
		this.storageUnitId = storageUnitId;
		this.productionProductTypeId = productionProductTypeId;
	}

}
