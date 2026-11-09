package com.olivamore.api.product.productattribute;

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

/** Global attribute tanimi; kategori eslemesi eklenirken olusur. */
@Entity
@Table(name = "attribute_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AttributeDefinition extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String attributeKey;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	@Convert(converter = AttributeDataTypeConverter.class)
	private AttributeDataType dataType;

	private String unit;

	private String description;

	AttributeDefinition(CreateProductAttributeRequest request) {
		attributeKey = request.attributeKey();
		label = request.label();
		dataType = request.dataType();
		unit = request.unit();
		description = request.description();
	}

	/** {@code null} gelen alanlar degismez. */
	void apply(UpdateProductAttributeRequest request) {
		if (request.label() != null) {
			label = request.label();
		}
		if (request.unit() != null) {
			unit = request.unit();
		}
		if (request.description() != null) {
			description = request.description();
		}
	}

}
