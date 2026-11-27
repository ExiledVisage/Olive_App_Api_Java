package com.olivamore.api.sales.order;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

record OrderItemRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductId,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer quantity,

	/** KDV dahil birim fiyat; verilmezse urunun liste fiyati. */
	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@Digits(integer = 10, fraction = 2, message = MessageKeys.NUMERIC)
	BigDecimal unitPrice) {
}
