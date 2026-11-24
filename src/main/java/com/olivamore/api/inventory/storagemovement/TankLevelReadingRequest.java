package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Cetvel okumasi (mm, dipten dolu yukseklik). */
record TankLevelReadingRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal measuredMm,

	String note) {

	TankLevelReadingRequest {
		note = Texts.trim(note);
	}

}
