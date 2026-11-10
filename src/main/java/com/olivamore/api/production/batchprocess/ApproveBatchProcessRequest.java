package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Size;

record ApproveBatchProcessRequest(
	@Size(max = 2000, message = MessageKeys.SIZE_MAX)
	String note) {

	ApproveBatchProcessRequest {
		note = Texts.trim(note);
	}

}
