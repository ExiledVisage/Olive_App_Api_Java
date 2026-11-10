package com.olivamore.api.process.processtypefield;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.process.FieldDataType;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

record CreateProcessTypeFieldRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String fieldKey,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String label,

	@NotNull(message = MessageKeys.REQUIRED)
	FieldDataType dataType,

	Boolean isRequired,

	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String unit,

	@Min(value = 0, message = MessageKeys.MIN)
	Integer sortOrder,

	Boolean isActive,

	JsonNode options,

	String defaultValue) {

	CreateProcessTypeFieldRequest {
		fieldKey = Texts.trim(fieldKey);
		label = Texts.trim(label);
		unit = Texts.trim(unit);
		defaultValue = Texts.trim(defaultValue);
	}

}
