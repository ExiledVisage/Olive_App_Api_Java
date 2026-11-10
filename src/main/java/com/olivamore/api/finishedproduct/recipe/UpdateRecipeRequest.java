package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** {@code null} gelen alan degismez; verilmeyen icerik ve kurallar eski versiyondan kopyalanir. */
record UpdateRecipeRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Patch<String> description,

	@Size(min = 1, message = MessageKeys.ITEMS_MIN)
	List<@Valid Content> contents,

	List<@Valid AttributeRule> attributeRules) {

	UpdateRecipeRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}


	record Content(
		@NotNull(message = MessageKeys.REQUIRED_WITH_CONTENTS)
		Long productionProductTypeId,

		@NotNull(message = MessageKeys.REQUIRED_WITH_CONTENTS)
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
