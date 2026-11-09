package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.StrictBooleanDeserializer;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

/** Yeni materyalin attribute degeri. {@code value_json} dizi ya da nesne olmali (serviste kontrol edilir). */
record AttributeValueInput(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long productionProductAttributeDefinitionId,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean valueBoolean,

	Long valueInteger,

	BigDecimal valueDecimal,

	String valueText,

	JsonNode valueJson) {
}
