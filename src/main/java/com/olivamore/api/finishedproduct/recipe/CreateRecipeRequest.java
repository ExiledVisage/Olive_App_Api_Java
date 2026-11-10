package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
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
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	String description,

	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid Content> contents,

	List<@Valid AttributeRule> attributeRules) {

	CreateRecipeRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}


	record Content(
		@NotNull(message = MessageKeys.REQUIRED)
		Long productionProductTypeId,

		@NotNull(message = MessageKeys.REQUIRED)
		@DecimalMin(value = "0.0001", message = MessageKeys.MIN)
		@DecimalMax(value = "1", message = MessageKeys.MAX)
		BigDecimal ratio) implements RecipeLines.Content {
	}

	record AttributeRule(
		@NotNull(message = MessageKeys.REQUIRED)
		Long attributeDefinitionId,

		@NotNull(message = MessageKeys.REQUIRED)
		@DecimalMin(value = "0", message = MessageKeys.MIN)
		BigDecimal value,

		@DecimalMin(value = "0", message = MessageKeys.MIN)
		BigDecimal minDeviation,

		@DecimalMin(value = "0", message = MessageKeys.MIN)
		BigDecimal maxDeviation) implements RecipeLines.Rule {
	}

}
