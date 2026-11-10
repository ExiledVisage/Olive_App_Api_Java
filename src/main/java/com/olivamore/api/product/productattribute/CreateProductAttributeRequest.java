package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateProductAttributeRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String attributeKey,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String label,

	String description,

	@NotNull(message = MessageKeys.REQUIRED)
	AttributeDataType dataType,

	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String unit,

	Boolean isRequired,

	@Min(value = 0, message = MessageKeys.MIN)
	Integer sortOrder) {

	CreateProductAttributeRequest {
		attributeKey = Texts.trim(attributeKey);
		label = Texts.trim(label);
		unit = Texts.trim(unit);
		description = Texts.trim(description);
	}

}
