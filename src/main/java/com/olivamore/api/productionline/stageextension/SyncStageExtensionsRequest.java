package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

record SyncStageExtensionsRequest(
	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid StageExtensionRequest> extensions) {
}
