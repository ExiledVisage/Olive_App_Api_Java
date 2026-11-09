package com.olivamore.api.process;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Asama uzantisi tipindeki alanin API cevabi. */
public record StageExtensionTypeFieldResponse(
	Long id,
	Long stageExtensionTypeId,
	String fieldKey,
	String label,
	String dataType,
	boolean isRequired,
	int sortOrder,
	Boolean defaultValueBoolean,
	Long defaultValueInteger,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	BigDecimal defaultValueDecimal,
	String defaultValueText,
	JsonNode defaultValueJson,
	JsonNode optionsJson,
	String description) {
}
