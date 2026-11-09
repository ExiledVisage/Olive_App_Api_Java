package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.inventory.material.StrictBooleanDeserializer;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

/** Yalnizca gonderilen alanlar; {@code description} {@code null} ile temizlenir. */
record UpdateStorageUnitRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String code,

	@DecimalMin(value = "0", inclusive = false, message = LaravelMessages.GT_0)
	BigDecimal capacity,

	MaterialUnit capacityUnit,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean isBlendTank,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean allowMultipleProductionProductTypes,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean allowMultipleProductionProducts,

	Patch<String> description,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean isActive) {
}
