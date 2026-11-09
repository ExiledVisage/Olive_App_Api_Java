package com.olivamore.api.sales.order;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code picks} hazirlamada raf secimi; verilmezse FIFO. */
record UpdateOrderStatusRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	OrderStatus status,

	@Size(max = 1000, message = LaravelMessages.MAX_1000)
	String note,

	List<@Valid Pick> picks) {

	record Pick(
		@NotNull(message = LaravelMessages.REQUIRED)
		Long orderItemId,

		@NotNull(message = LaravelMessages.REQUIRED)
		Long slotId,

		@NotNull(message = LaravelMessages.REQUIRED)
		@Min(value = 1, message = LaravelMessages.MIN_1)
		Integer quantity) {
	}

}
