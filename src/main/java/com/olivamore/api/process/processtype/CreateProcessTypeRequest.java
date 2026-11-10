package com.olivamore.api.process.processtype;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateProcessTypeRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	String description,

	@NotNull(message = MessageKeys.REQUIRED)
	ProcessCategory category,

	Boolean isActive) {

	CreateProcessTypeRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
