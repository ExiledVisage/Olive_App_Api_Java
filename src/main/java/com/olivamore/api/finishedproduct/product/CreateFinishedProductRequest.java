package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Yeni urun; {@code is_active} verilmezse aktif. */
record CreateFinishedProductRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductRecipeId,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0.001", message = MessageKeys.MIN)
	BigDecimal volumeLitre,

	Boolean isActive,

	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid Component> components) {

	CreateFinishedProductRequest {
		name = Texts.trim(name);
	}


	record Component(
		@NotNull(message = MessageKeys.REQUIRED)
		Long packagingItemId,

		@NotNull(message = MessageKeys.REQUIRED)
		@Min(value = 1, message = MessageKeys.MIN)
		Integer quantity) implements ComponentLine {
	}

}
