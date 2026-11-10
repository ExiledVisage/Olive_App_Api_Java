package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record PreferredRecipeRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long finishedProductRecipeId,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String note) {

	PreferredRecipeRequest {
		note = Texts.trim(note);
	}

}
