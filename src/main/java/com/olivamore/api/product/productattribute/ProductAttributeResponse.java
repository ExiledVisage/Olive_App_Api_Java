package com.olivamore.api.product.productattribute;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** Kategori-attribute eslemesinin API cevabi. {@code definition} istenmediyse cevapta yok. */
public record ProductAttributeResponse(
	Long id,
	Long productionProductId,
	boolean isRequired,
	int sortOrder,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Definition definition,
	String attributeKey,
	String label,
	AttributeDataType dataType,
	String unit,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public record Definition(
		Long id,
		String attributeKey,
		String label,
		AttributeDataType dataType,
		String unit,
		String description) {
	}

	static ProductAttributeResponse of(ProductAttribute mapping, boolean withDefinition) {
		AttributeDefinition d = mapping.getDefinition();
		return new ProductAttributeResponse(mapping.getId(), mapping.getProductionProductId(), mapping.isRequired(),
			mapping.getSortOrder(),
			withDefinition
				? new Definition(d.getId(), d.getAttributeKey(), d.getLabel(), d.getDataType(), d.getUnit(),
					d.getDescription())
				: null,
			d.getAttributeKey(), d.getLabel(), d.getDataType(), d.getUnit(), mapping.getCreatedAt(),
			mapping.getUpdatedAt());
	}

}
