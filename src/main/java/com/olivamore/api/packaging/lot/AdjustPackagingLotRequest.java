package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.NotNull;

/** Sayim farki, fire ya da iade; {@code delta} isaretli. */
record AdjustPackagingLotRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Integer delta,

	LotEnums.AdjustReason reason,

	String note) {

	AdjustPackagingLotRequest {
		note = Texts.trim(note);
	}

}
