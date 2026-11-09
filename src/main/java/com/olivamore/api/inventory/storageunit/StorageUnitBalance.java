package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.material.MaterialUnit;
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
import lombok.Setter;

import java.math.BigDecimal;

/** Tankin urun kategorisi bazinda bakiyesi. */
@Entity
@Table(name = "storage_unit_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageUnitBalance extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long storageUnitId;

	@Column(nullable = false)
	private Long productionProductId;

	@Setter
	@Column(nullable = false)
	private BigDecimal currentQuantity;

	@Column(nullable = false)
	@Convert(converter = MaterialUnit.UnitConverter.class)
	private MaterialUnit unit;

	public StorageUnitBalance(long storageUnitId, long productionProductId, MaterialUnit unit,
		BigDecimal currentQuantity) {
		this.storageUnitId = storageUnitId;
		this.productionProductId = productionProductId;
		this.unit = unit;
		this.currentQuantity = currentQuantity;
	}

}
