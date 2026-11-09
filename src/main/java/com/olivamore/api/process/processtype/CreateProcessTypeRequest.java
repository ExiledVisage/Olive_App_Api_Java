package com.olivamore.api.process.processtype;

import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateProcessTypeRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	String description,

	@NotNull(message = LaravelMessages.REQUIRED)
	ProcessCategory category,

	Boolean isActive) {
}
