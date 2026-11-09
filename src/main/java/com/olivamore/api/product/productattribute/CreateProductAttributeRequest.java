package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateProductAttributeRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 100, message = LaravelMessages.MAX_100)
	String attributeKey,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String label,

	String description,

	@NotNull(message = LaravelMessages.REQUIRED)
	AttributeDataType dataType,

	@Size(max = 50, message = LaravelMessages.MAX_50)
	String unit,

	Boolean isRequired,

	@Min(value = 0, message = LaravelMessages.MIN_0)
	Integer sortOrder) {
}
