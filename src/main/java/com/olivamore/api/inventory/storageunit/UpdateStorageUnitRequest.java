package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Yalnizca gonderilen alanlar; {@code description} {@code null} ile temizlenir. */
record UpdateStorageUnitRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String code,

	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal capacity,

	MaterialUnit capacityUnit,

	Boolean isBlendTank,

	Boolean allowMultipleProductionProductTypes,

	Boolean allowMultipleProductionProducts,

	Patch<String> description,

	Boolean isActive) {

	UpdateStorageUnitRequest {
		name = Texts.trim(name);
		code = Texts.trim(code);
		description = Texts.trim(description);
	}

}
