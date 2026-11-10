package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotNull;

record UpdateRunStatusRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	RunStatus status) {
}
