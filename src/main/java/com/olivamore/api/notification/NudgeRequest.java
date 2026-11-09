package com.olivamore.api.notification;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code userId} verilmezse isin sorumlu rolundeki herkes uyarilir. */
record NudgeRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	NudgeSubject subjectType,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Min(value = 1, message = LaravelMessages.MIN_1)
	Long subjectId,

	Long userId,

	@Size(max = 500, message = LaravelMessages.MAX_500)
	String note) {
}
