package com.olivamore.api.productionline.stage;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateProductionStageRequest(
	Long processTypeId,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer stageOrder,

	Boolean isRequired,

	Boolean requiresApproval) {

	CreateProductionStageRequest {
		name = Texts.trim(name);
	}

}
