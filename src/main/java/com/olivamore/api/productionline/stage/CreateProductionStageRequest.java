package com.olivamore.api.productionline.stage;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateProductionStageRequest(
	Long processTypeId,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer stageOrder,

	Boolean isRequired,

	Boolean requiresApproval) {
}
