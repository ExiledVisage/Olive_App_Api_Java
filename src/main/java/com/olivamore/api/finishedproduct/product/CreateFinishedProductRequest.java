package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.shared.validation.LaravelMessages;
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
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long finishedProductRecipeId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@DecimalMin(value = "0.001", message = LaravelMessages.MIN_VOLUME)
	BigDecimal volumeLitre,

	Boolean isActive,

	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid Component> components) {

	record Component(
		@NotNull(message = LaravelMessages.REQUIRED)
		Long packagingItemId,

		@NotNull(message = LaravelMessages.REQUIRED)
		@Min(value = 1, message = LaravelMessages.MIN_1)
		Integer quantity) implements ComponentLine {
	}

}
