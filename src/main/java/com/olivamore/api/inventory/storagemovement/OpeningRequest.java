package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Tank acilisi. */
record OpeningRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long storageUnitId,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long productionProductTypeId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = LaravelMessages.GT_0)
	BigDecimal quantity,

	@NotNull(message = LaravelMessages.REQUIRED)
	MaterialUnit unit,

	List<@Valid AttributeValueInput> attributeValues,

	String note) {
}
