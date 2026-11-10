package com.olivamore.api.notification;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code userId} verilmezse isin sorumlu rolundeki herkes uyarilir. */
record NudgeRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	NudgeSubject subjectType,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Long subjectId,

	Long userId,

	@Size(max = 500, message = MessageKeys.SIZE_MAX)
	String note) {

	NudgeRequest {
		note = Texts.trim(note);
	}

}
