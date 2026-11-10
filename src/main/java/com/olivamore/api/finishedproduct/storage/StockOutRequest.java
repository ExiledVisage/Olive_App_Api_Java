package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record StockOutRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductStorageSlotId,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer quantity) {
}
