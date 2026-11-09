package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.inventory.material.StrictBooleanDeserializer;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

/** Yeni tank; bayraklar verilmezse false, {@code is_active} true. */
record CreateStorageUnitRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String code,

	@NotNull(message = LaravelMessages.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = LaravelMessages.GT_0)
	BigDecimal capacity,

	@NotNull(message = LaravelMessages.REQUIRED)
	MaterialUnit capacityUnit,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean isBlendTank,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean allowMultipleProductionProductTypes,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean allowMultipleProductionProducts,

	String description,

	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean isActive) {
}
