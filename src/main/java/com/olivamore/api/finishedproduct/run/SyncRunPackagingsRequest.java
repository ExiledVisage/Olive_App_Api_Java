package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Elle ambalaj lotu tahsisi; liste tahsisin tamami. */
record SyncRunPackagingsRequest(
	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid Packaging> packagings) {

	record Packaging(
		@NotNull(message = MessageKeys.REQUIRED)
		Long packagingLotId,

		@NotNull(message = MessageKeys.REQUIRED)
		@Min(value = 1, message = MessageKeys.MIN)
		Integer quantity) {
	}

}
