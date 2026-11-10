package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Tanklar arasi transfer. */
record TransferRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long fromStorageUnitId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long toStorageUnitId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long materialId,

	Long batchProcessId,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal quantity,

	String note) {

	TransferRequest {
		note = Texts.trim(note);
	}

}
