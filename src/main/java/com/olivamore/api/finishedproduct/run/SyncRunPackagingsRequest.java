package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Elle ambalaj lotu tahsisi; liste tahsisin tamami. */
record SyncRunPackagingsRequest(
	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid Packaging> packagings) {

	record Packaging(
		@NotNull(message = LaravelMessages.REQUIRED)
		Long packagingLotId,

		@NotNull(message = LaravelMessages.REQUIRED)
		@Min(value = 1, message = LaravelMessages.MIN_1)
		Integer quantity) {
	}

}
