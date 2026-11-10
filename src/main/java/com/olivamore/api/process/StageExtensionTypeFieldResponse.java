package com.olivamore.api.process;

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
	BigDecimal defaultValueDecimal,
	String defaultValueText,
	JsonNode defaultValueJson,
	JsonNode optionsJson,
	String description) {
}
