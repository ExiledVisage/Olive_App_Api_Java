package com.olivamore.api.process.processtypefield;

import com.olivamore.api.process.FieldDataType;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateProcessTypeFieldRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String fieldKey,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String label,

	FieldDataType dataType,

	Boolean isRequired,

	Patch<@Size(max = 50, message = LaravelMessages.MAX_50) String> unit,

	@Min(value = 0, message = LaravelMessages.MIN_0)
	Integer sortOrder,

	Boolean isActive,

	Patch<JsonNode> options,

	Patch<String> defaultValue) {
}
