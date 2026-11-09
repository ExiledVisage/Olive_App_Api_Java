package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotNull;

/** Sayim farki, fire ya da iade; {@code delta} isaretli. */
record AdjustPackagingLotRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Integer delta,

	LotEnums.AdjustReason reason,

	String note) {
}
