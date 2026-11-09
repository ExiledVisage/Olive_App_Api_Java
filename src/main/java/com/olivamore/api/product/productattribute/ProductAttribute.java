package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bir urun kategorisinin kullandigi attribute. */
@Entity
@Table(name = "production_product_attribute_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductAttribute extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionProductId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attribute_definition_id")
	private AttributeDefinition definition;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(nullable = false)
	private int sortOrder;

	ProductAttribute(long productId, AttributeDefinition definition, CreateProductAttributeRequest request) {
		productionProductId = productId;
		this.definition = definition;
		required = Boolean.TRUE.equals(request.isRequired());
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
	}

	/** {@code null} gelen alanlar degismez. */
	void apply(UpdateProductAttributeRequest request) {
		definition.apply(request);
		if (request.isRequired() != null) {
			required = request.isRequired();
		}
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
	}

}
