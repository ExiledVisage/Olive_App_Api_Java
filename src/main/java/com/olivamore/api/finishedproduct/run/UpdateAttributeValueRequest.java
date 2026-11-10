package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

record UpdateAttributeValueRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	BigDecimal actualValue) {
}
