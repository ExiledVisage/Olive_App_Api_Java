package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Grubun yeni payi (tartilan kilo): fark teslimatin zeytinine doner ya da ondan alinir. */
record CorrectBatchGroupRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.MIN)
	BigDecimal quantityKg) {
}
