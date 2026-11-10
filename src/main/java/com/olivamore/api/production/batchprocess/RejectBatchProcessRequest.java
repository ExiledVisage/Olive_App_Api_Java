package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record RejectBatchProcessRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	@Size(max = 2000, message = LaravelMessages.MAX_2000)
	String note) {
}
