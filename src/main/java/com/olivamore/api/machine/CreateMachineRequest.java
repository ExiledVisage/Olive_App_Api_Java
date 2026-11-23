package com.olivamore.api.machine;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/** Parca verilmezse makinenin tek parcasi acilir. */
record CreateMachineRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long machineTypeId,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String code,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String brand,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String model,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal capacityKgPerHour,

	String note,

	Boolean isActive,

	/** Tipin makine bilgisi alanlari: {"kirici_tipi": "cekicli"}. */
	JsonNode attributes,

	List<@Valid CreateMachinePartRequest> parts) {

	CreateMachineRequest {
		code = Texts.trim(code);
		name = Texts.trim(name);
		brand = Texts.trim(brand);
		model = Texts.trim(model);
		note = Texts.trim(note);
	}

}
