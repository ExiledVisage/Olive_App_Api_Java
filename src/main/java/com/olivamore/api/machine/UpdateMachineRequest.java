package com.olivamore.api.machine;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; tip degistirilemez. */
record UpdateMachineRequest(
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String code,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Patch<@Size(max = 255, message = MessageKeys.SIZE_MAX) String> brand,

	Patch<@Size(max = 255, message = MessageKeys.SIZE_MAX) String> model,

	Patch<@DecimalMin(value = "0", message = MessageKeys.MIN) BigDecimal> capacityKgPerHour,

	Patch<String> note,

	/** Verilirse makinenin bilgi alanlarinin tamami bununla degisir. */
	JsonNode attributes,

	Boolean isActive) {

	UpdateMachineRequest {
		code = Texts.trim(code);
		name = Texts.trim(name);
		brand = Texts.trim(brand);
		model = Texts.trim(model);
		note = Texts.trim(note);
	}

}
