package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** {@code null} gelen alan degismez; verilen bilesenler eskilerin yerine gecer. */
record UpdateFinishedProductRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	Long finishedProductRecipeId,

	@DecimalMin(value = "0.001", message = LaravelMessages.MIN_VOLUME)
	BigDecimal volumeLitre,

	Boolean isActive,

	@Size(min = 1, message = LaravelMessages.MIN_ITEMS_1)
	List<@Valid Component> components) {

	record Component(
		@NotNull(message = LaravelMessages.REQUIRED_WITH_COMPONENTS)
		Long packagingItemId,

		@NotNull(message = LaravelMessages.REQUIRED_WITH_COMPONENTS)
		@Min(value = 1, message = LaravelMessages.MIN_1)
		Integer quantity) implements ComponentLine {
	}

}
