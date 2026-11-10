package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateSlotRequest(
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String name,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0.001", message = MessageKeys.MIN)
	BigDecimal volumeLitre,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer maxQuantity,

	Long finishedProductId) {

	CreateSlotRequest {
		name = Texts.trim(name);
	}

}
