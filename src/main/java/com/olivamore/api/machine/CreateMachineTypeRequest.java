package com.olivamore.api.machine;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Tip ve (varsa) olcum alanlari birlikte. Anahtar kucuk harf, rakam ve alt cizgi. */
record CreateMachineTypeRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	@Pattern(regexp = "[a-z][a-z0-9_]*", message = MachineRequests.KEY_FORMAT)
	String key,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	String description,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer sortOrder,

	Boolean isActive,

	List<@Valid CreateMachineTypeFieldRequest> fields) {

	CreateMachineTypeRequest {
		key = Texts.trim(key);
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
