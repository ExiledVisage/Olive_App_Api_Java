package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Tank acilisi. */
record OpeningRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long storageUnitId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long productionProductTypeId,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal quantity,

	@NotNull(message = MessageKeys.REQUIRED)
	MaterialUnit unit,

	List<@Valid AttributeValueInput> attributeValues,

	String note) {

	OpeningRequest {
		note = Texts.trim(note);
	}

}
