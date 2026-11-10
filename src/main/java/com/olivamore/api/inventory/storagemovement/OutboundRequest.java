package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Tanktan cikis. */
record OutboundRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long storageUnitId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long materialId,

	Long batchProcessId,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal quantity,

	String note) {

	OutboundRequest {
		note = Texts.trim(note);
	}

}
