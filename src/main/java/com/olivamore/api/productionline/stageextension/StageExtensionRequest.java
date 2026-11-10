package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

record StageExtensionRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long stageExtensionTypeId,

	Boolean isRequired,

	Boolean isEnabled,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer sortOrder,

	List<@Valid StageExtensionValueRequest> values,

	List<@NotNull(message = MessageKeys.INTEGER) Long> inputProductionProductIds,

	List<@NotNull(message = MessageKeys.INTEGER) Long> outputProductionProductIds) {
}
