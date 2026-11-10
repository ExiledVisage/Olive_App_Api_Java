package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record TransferRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long sourceSlotId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long targetSlotId,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer quantity) {
}
