package com.olivamore.api.process.processtypefield;

import com.olivamore.api.process.FieldDataType;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

record CreateProcessTypeFieldRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String fieldKey,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String label,

	@NotNull(message = LaravelMessages.REQUIRED)
	FieldDataType dataType,

	Boolean isRequired,

	@Size(max = 50, message = LaravelMessages.MAX_50)
	String unit,

	@Min(value = 0, message = LaravelMessages.MIN_0)
	Integer sortOrder,

	Boolean isActive,

	JsonNode options,

	String defaultValue) {
}
