package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.material.Quantities;
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

/** Tank attribute kurali (min/max 4 basamak). */
@Entity
@Table(name = "storage_unit_attribute_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageUnitAttributeRule extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long storageUnitId;

	@Column(name = "production_product_attribute_definition_id", nullable = false)
	private Long definitionId;

	private BigDecimal minValueDecimal;

	private BigDecimal maxValueDecimal;

	@Column(nullable = false)
	@Convert(converter = ApplyMode.ModeConverter.class)
	private ApplyMode applyMode = ApplyMode.INCOMING_AND_AVERAGE;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	private String note;

	StorageUnitAttributeRule(long storageUnitId, long definitionId) {
		this.storageUnitId = storageUnitId;
		this.definitionId = definitionId;
	}

	void apply(BigDecimal min, BigDecimal max, ApplyMode mode, boolean isActive, String text) {
		minValueDecimal = Quantities.material(min);
		maxValueDecimal = Quantities.material(max);
		applyMode = mode;
		active = isActive;
		note = text;
	}

}
