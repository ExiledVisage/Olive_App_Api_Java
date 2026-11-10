package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code null} gelen alan degismez. {@code attributeKey} degistirilemez; yalnizca baska bir eslemeyle
 * cakisiyor mu diye bakilir.
 */
record UpdateProductAttributeRequest(
	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String attributeKey,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String label,

	String description,

	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String unit,

	Boolean isRequired,

	@Min(value = 0, message = MessageKeys.MIN)
	Integer sortOrder) {

	UpdateProductAttributeRequest {
		attributeKey = Texts.trim(attributeKey);
		label = Texts.trim(label);
		unit = Texts.trim(unit);
		description = Texts.trim(description);
	}

}
