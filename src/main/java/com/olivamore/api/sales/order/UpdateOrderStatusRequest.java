package com.olivamore.api.sales.order;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code picks} hazirlamada raf secimi; verilmezse FIFO. */
record UpdateOrderStatusRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	OrderStatus status,

	@Size(max = 1000, message = MessageKeys.SIZE_MAX)
	String note,

	List<@Valid Pick> picks) {

	UpdateOrderStatusRequest {
		note = Texts.trim(note);
	}


	record Pick(
		@NotNull(message = MessageKeys.REQUIRED)
		Long orderItemId,

		@NotNull(message = MessageKeys.REQUIRED)
		Long slotId,

		@NotNull(message = MessageKeys.REQUIRED)
		@Min(value = 1, message = MessageKeys.MIN)
		Integer quantity) {
	}

}
