package com.olivamore.api.machine;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez. */
record UpdateMachineTypeRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Patch<String> description,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer sortOrder,

	Boolean isActive) {

	UpdateMachineTypeRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
