package com.olivamore.api.sales.invoice;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CancelInvoiceRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 500, message = MessageKeys.SIZE_MAX)
	String reason) {

	CancelInvoiceRequest {
		reason = Texts.trim(reason);
	}

}
