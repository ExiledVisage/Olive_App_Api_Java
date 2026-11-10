package com.olivamore.api.process.processtypefield;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.process.FieldDataType;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateProcessTypeFieldRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String fieldKey,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String label,

	FieldDataType dataType,

	Boolean isRequired,

	Patch<@Size(max = 50, message = MessageKeys.SIZE_MAX) String> unit,

	@Min(value = 0, message = MessageKeys.MIN)
	Integer sortOrder,

	Boolean isActive,

	Patch<JsonNode> options,

	Patch<String> defaultValue) {

	UpdateProcessTypeFieldRequest {
		fieldKey = Texts.trim(fieldKey);
		label = Texts.trim(label);
		unit = Texts.trim(unit);
		defaultValue = Texts.trim(defaultValue);
	}

}
