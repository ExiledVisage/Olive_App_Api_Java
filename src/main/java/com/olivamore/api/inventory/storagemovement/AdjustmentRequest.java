package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Duzeltme: artis yeni materyal (tip + birim zorunlu), azalis var olan materyalden (material_id zorunlu). */
record AdjustmentRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long storageUnitId,

	Long batchProcessId,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal quantity,

	@NotNull(message = MessageKeys.REQUIRED)
	Boolean isIncrease,

	Long productionProductTypeId,

	MaterialUnit unit,

	Long materialId,

	List<@Valid AttributeValueInput> attributeValues,

	String note) {

	AdjustmentRequest {
		note = Texts.trim(note);
	}

}
