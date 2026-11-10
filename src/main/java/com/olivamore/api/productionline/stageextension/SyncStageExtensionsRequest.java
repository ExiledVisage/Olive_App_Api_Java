package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

record SyncStageExtensionsRequest(
	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid StageExtensionRequest> extensions) {
}
