package com.olivamore.api.productionline.stage;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code processTypeId} ise {@code null} ile temizlenebilir. */
record UpdateProductionStageRequest(
	Patch<Long> processTypeId,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer stageOrder,

	Boolean isRequired,

	Boolean requiresApproval) {
}
