package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotNull;

record UpdateRunStatusRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	RunStatus status) {
}
