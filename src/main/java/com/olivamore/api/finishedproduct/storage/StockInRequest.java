package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Tek raf ({@code finished_product_storage_slot_id} + {@code quantity}) ya da {@code placements}. */
record StockInRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductId,

	Long finishedProductRunId,

	Long finishedProductStorageSlotId,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer quantity,

	List<@Valid Placement> placements,

	@Size(max = 1000, message = MessageKeys.SIZE_MAX)
	String note) {

	StockInRequest {
		note = Texts.trim(note);
	}


	record Placement(
		@NotNull(message = MessageKeys.REQUIRED_WITH_PLACEMENTS)
		Long finishedProductStorageSlotId,

		@NotNull(message = MessageKeys.REQUIRED_WITH_PLACEMENTS)
		@Min(value = 1, message = MIN_QUANTITY_MESSAGE)
		Integer quantity) {

		static final String MIN_QUANTITY_MESSAGE = "Raf miktarı en az 1 olmalıdır.";

	}

}
