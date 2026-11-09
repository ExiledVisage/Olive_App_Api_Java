package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Dolum planlama; {@code source.quantity} verilmezse sise adedi x hacim, fire payiyla. */
record CreateRunRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long finishedProductId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer quantity,

	String note,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Valid
	Source source) {

	record Source(
		@NotNull(message = LaravelMessages.REQUIRED)
		Long storageUnitId,

		@DecimalMin(value = "0.001", message = LaravelMessages.MIN_VOLUME)
		BigDecimal quantity,

		@DecimalMin(value = "0", message = LaravelMessages.MIN_0)
		@DecimalMax(value = "2", message = MAX_LOSS_MESSAGE)
		BigDecimal lossPercent) {

		static final String MAX_LOSS_MESSAGE = "Fire payı en fazla %2 olabilir.";

	}

}
