package com.olivamore.api.analytics.report;

import com.olivamore.api.shared.validation.Texts;
/** {@code null} gelen alan degismez. */
record UpdateReportRequest(String name, ReportConfig config) {

	UpdateReportRequest {
		name = Texts.trim(name);
	}

}
