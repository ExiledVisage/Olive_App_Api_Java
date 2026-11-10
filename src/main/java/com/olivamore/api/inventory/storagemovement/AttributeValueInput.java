package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Yeni materyalin attribute degeri. {@code value_json} dizi ya da nesne olmali (serviste kontrol edilir). */
record AttributeValueInput(
	@NotNull(message = MessageKeys.REQUIRED)
	Long productionProductAttributeDefinitionId,

	Boolean valueBoolean,

	Long valueInteger,

	BigDecimal valueDecimal,

	String valueText,

	JsonNode valueJson) {
}
