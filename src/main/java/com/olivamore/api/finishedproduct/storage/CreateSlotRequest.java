package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateSlotRequest(
	@Size(max = 50, message = LaravelMessages.MAX_50)
	String name,

	@NotNull(message = LaravelMessages.REQUIRED)
	@DecimalMin(value = "0.001", message = LaravelMessages.MIN_VOLUME)
	BigDecimal volumeLitre,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer maxQuantity,

	Long finishedProductId) {
}
