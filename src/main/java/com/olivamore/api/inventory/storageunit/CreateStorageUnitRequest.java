package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Yeni tank; bayraklar verilmezse false, {@code is_active} true, tur son tank. */
record CreateStorageUnitRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String code,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal capacity,

	@NotNull(message = MessageKeys.REQUIRED)
	MaterialUnit capacityUnit,

	StorageUnitKind kind,

	Boolean isBlendTank,

	Boolean allowMultipleProductionProductTypes,

	Boolean allowMultipleProductionProducts,

	String description,

	Boolean isActive) {

	CreateStorageUnitRequest {
		name = Texts.trim(name);
		code = Texts.trim(code);
		description = Texts.trim(description);
	}

}
