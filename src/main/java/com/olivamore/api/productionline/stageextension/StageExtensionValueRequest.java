package com.olivamore.api.productionline.stageextension;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Alan {@code stageExtensionTypeFieldId} ya da {@code fieldKey} ile secilir. */
record StageExtensionValueRequest(
	Long stageExtensionTypeFieldId,
	String fieldKey,
	Boolean valueBoolean,
	Long valueInteger,
	BigDecimal valueDecimal,
	String valueText,
	JsonNode valueJson) {
}
