package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record PreferredRecipeRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long finishedProductRecipeId,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String note) {
}
