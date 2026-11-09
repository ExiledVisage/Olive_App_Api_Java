package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

record StageExtensionRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long stageExtensionTypeId,

	Boolean isRequired,

	Boolean isEnabled,

	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer sortOrder,

	List<@Valid StageExtensionValueRequest> values,

	List<@NotNull(message = LaravelMessages.INTEGER) Long> inputProductionProductIds,

	List<@NotNull(message = LaravelMessages.INTEGER) Long> outputProductionProductIds) {
}
