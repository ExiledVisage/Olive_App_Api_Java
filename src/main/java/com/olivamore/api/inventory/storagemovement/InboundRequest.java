package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** {@code material_id} ile var olan materyal, {@code production_product_type_id} ile yeni materyal. */
record InboundRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long storageUnitId,

	Long materialId,

	Long productionProductTypeId,

	MaterialUnit unit,

	List<@Valid AttributeValueInput> attributeValues,

	Long batchProcessId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = LaravelMessages.GT_0)
	BigDecimal quantity,

	String note) {
}
