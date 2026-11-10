package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RejectBatchProcessRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 2000, message = MessageKeys.SIZE_MAX)
	String note) {

	RejectBatchProcessRequest {
		note = Texts.trim(note);
	}

}
