package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Yeni tarif: versiyon 1, aktif. Oran toplami serviste kontrol edilir. */
record CreateRecipeRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	String description,

	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid Content> contents,

	List<@Valid AttributeRule> attributeRules) {

	record Content(
		@NotNull(message = LaravelMessages.REQUIRED)
		Long productionProductTypeId,

		@NotNull(message = LaravelMessages.REQUIRED)
		@DecimalMin(value = "0.0001", message = LaravelMessages.MIN_RATIO)
		@DecimalMax(value = "1", message = LaravelMessages.MAX_1)
		BigDecimal ratio) implements RecipeLines.Content {
	}

	record AttributeRule(
		@NotNull(message = LaravelMessages.REQUIRED)
		Long attributeDefinitionId,

		@NotNull(message = LaravelMessages.REQUIRED)
		@DecimalMin(value = "0", message = LaravelMessages.MIN_0)
		BigDecimal value,

		@DecimalMin(value = "0", message = LaravelMessages.MIN_0)
		BigDecimal minDeviation,

		@DecimalMin(value = "0", message = LaravelMessages.MIN_0)
		BigDecimal maxDeviation) implements RecipeLines.Rule {
	}

}
