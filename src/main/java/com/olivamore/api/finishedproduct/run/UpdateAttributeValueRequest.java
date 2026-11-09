package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

record UpdateAttributeValueRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	BigDecimal actualValue) {
}
