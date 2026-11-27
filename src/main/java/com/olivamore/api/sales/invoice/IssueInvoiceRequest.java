package com.olivamore.api.sales.invoice;

import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.validation.Texts;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

/** Bos govde de olur: tarih verilmezse bugun (fabrikanin yerel gunu). */
record IssueInvoiceRequest(
	@JsonDeserialize(using = IsoDateTimeDeserializer.class)
	LocalDateTime issueDate,

	String note) {

	static final IssueInvoiceRequest EMPTY = new IssueInvoiceRequest(null, null);

	IssueInvoiceRequest {
		note = Texts.trim(note);
	}

}
