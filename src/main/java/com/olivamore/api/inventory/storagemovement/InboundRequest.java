package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** {@code material_id} ile var olan materyal, {@code production_product_type_id} ile yeni materyal. */
record InboundRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long storageUnitId,

	Long materialId,

	Long productionProductTypeId,

	MaterialUnit unit,

	List<@Valid AttributeValueInput> attributeValues,

	Long batchProcessId,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal quantity,

	String note) {

	InboundRequest {
		note = Texts.trim(note);
	}

}
