package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code null} gelen alan degismez. {@code attributeKey} degistirilemez; yalnizca baska bir eslemeyle
 * cakisiyor mu diye bakilir.
 */
record UpdateProductAttributeRequest(
	@Size(max = 100, message = LaravelMessages.MAX_100)
	String attributeKey,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String label,

	String description,

	@Size(max = 50, message = LaravelMessages.MAX_50)
	String unit,

	Boolean isRequired,

	@Min(value = 0, message = LaravelMessages.MIN_0)
	Integer sortOrder) {
}
