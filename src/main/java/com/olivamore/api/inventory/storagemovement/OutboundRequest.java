package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Tanktan cikis. */
record OutboundRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long storageUnitId,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long materialId,

	Long batchProcessId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = LaravelMessages.GT_0)
	BigDecimal quantity,

	String note) {
}
