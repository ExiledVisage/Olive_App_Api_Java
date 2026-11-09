package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Tank attribute kurallari (tam liste): listede olmayanlar silinir. */
record SyncAttributeRulesRequest(
	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid Rule> rules) {

	record Rule(
		@NotNull(message = LaravelMessages.REQUIRED)
		Long productionProductAttributeDefinitionId,

		BigDecimal minValueDecimal,

		BigDecimal maxValueDecimal,

		ApplyMode applyMode,

		Boolean isActive,

		String note) {
	}

}
