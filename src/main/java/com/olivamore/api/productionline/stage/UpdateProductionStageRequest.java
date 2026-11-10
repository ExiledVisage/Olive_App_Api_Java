package com.olivamore.api.productionline.stage;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code processTypeId} ise {@code null} ile temizlenebilir. */
record UpdateProductionStageRequest(
	Patch<Long> processTypeId,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer stageOrder,

	Boolean isRequired,

	Boolean requiresApproval) {

	UpdateProductionStageRequest {
		name = Texts.trim(name);
	}

}
