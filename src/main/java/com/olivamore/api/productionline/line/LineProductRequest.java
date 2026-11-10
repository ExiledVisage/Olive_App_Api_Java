package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record LineProductRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long productionProductId,

	Long productionProductTypeId,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer sortOrder) {
}
