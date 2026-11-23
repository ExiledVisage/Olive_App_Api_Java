package com.olivamore.api.machine;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; anahtar ve veri tipi degistirilemez. */
record UpdateMachineTypeFieldRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String label,

	Patch<@Size(max = 20, message = MessageKeys.SIZE_MAX) String> unit,

	Boolean isRequired,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer sortOrder,

	Patch<BigDecimal> minValue,

	Patch<BigDecimal> maxValue,

	Boolean isActive) {

	UpdateMachineTypeFieldRequest {
		label = Texts.trim(label);
		unit = Texts.trim(unit);
	}

}
