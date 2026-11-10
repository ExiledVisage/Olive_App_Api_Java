package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** {@code null} gelen alan degismez; verilen bilesenler eskilerin yerine gecer. */
record UpdateFinishedProductRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Long finishedProductRecipeId,

	@DecimalMin(value = "0.001", message = MessageKeys.MIN)
	BigDecimal volumeLitre,

	Boolean isActive,

	@Size(min = 1, message = MessageKeys.ITEMS_MIN)
	List<@Valid Component> components) {

	UpdateFinishedProductRequest {
		name = Texts.trim(name);
	}


	record Component(
		@NotNull(message = MessageKeys.REQUIRED_WITH_COMPONENTS)
		Long packagingItemId,

		@NotNull(message = MessageKeys.REQUIRED_WITH_COMPONENTS)
		@Min(value = 1, message = MessageKeys.MIN)
		Integer quantity) implements ComponentLine {
	}

}
