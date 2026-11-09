package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record TransferRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long finishedProductId,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long sourceSlotId,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long targetSlotId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer quantity) {
}
