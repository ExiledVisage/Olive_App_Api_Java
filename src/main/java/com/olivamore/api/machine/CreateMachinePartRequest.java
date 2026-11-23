package com.olivamore.api.machine;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateMachinePartRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String code,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer sortOrder,

	Boolean isActive) {

	CreateMachinePartRequest {
		code = Texts.trim(code);
		name = Texts.trim(name);
	}

}
