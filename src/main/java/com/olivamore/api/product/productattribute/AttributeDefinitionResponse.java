package com.olivamore.api.product.productattribute;

import java.time.LocalDateTime;

record AttributeDefinitionResponse(
	Long id,
	String attributeKey,
	String label,
	AttributeDataType dataType,
	String unit,
	String description,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	static AttributeDefinitionResponse of(AttributeDefinition d) {
		return new AttributeDefinitionResponse(d.getId(), d.getAttributeKey(), d.getLabel(), d.getDataType(),
			d.getUnit(), d.getDescription(), d.getCreatedAt(), d.getUpdatedAt());
	}

}
