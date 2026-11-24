package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Dolumda kirik / hatali ambalaj. {@code packaging_lot_id} verilmezse kosunun o kalem icin tahsis ettigi lot.
 * {@code reason}: broken, defective ya da other.
 */
record ScrapPackagingRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long packagingItemId,

	Long packagingLotId,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer quantity,

	@NotBlank(message = MessageKeys.REQUIRED)
	String reason,

	String note) {

	ScrapPackagingRequest {
		note = Texts.trim(note);
	}

}
