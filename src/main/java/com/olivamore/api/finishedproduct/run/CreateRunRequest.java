package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Dolum planlama; {@code source.quantity} verilmezse sise adedi x hacim, fire payiyla. */
record CreateRunRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductId,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer quantity,

	String note,

	@NotNull(message = MessageKeys.REQUIRED)
	@Valid
	Source source) {

	CreateRunRequest {
		note = Texts.trim(note);
	}


	record Source(
		@NotNull(message = MessageKeys.REQUIRED)
		Long storageUnitId,

		@DecimalMin(value = "0.001", message = MessageKeys.MIN)
		BigDecimal quantity,

		@DecimalMin(value = "0", message = MessageKeys.MIN)
		@DecimalMax(value = "2", message = MAX_LOSS_MESSAGE)
		BigDecimal lossPercent) {

		static final String MAX_LOSS_MESSAGE = "Fire payı en fazla %2 olabilir.";

	}

}
