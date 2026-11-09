package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record LineProductRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long productionProductId,

	Long productionProductTypeId,

	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer sortOrder) {
}
