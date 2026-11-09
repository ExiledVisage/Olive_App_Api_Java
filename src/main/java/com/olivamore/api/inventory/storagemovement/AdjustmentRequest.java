package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.inventory.material.StrictBooleanDeserializer;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.util.List;

/** Duzeltme: artis yeni materyal (tip + birim zorunlu), azalis var olan materyalden (material_id zorunlu). */
record AdjustmentRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long storageUnitId,

	Long batchProcessId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = LaravelMessages.GT_0)
	BigDecimal quantity,

	@NotNull(message = LaravelMessages.REQUIRED)
	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean isIncrease,

	Long productionProductTypeId,

	MaterialUnit unit,

	Long materialId,

	List<@Valid AttributeValueInput> attributeValues,

	String note) {
}
