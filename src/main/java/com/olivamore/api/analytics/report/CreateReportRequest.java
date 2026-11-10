package com.olivamore.api.analytics.report;

import com.olivamore.api.shared.validation.Texts;
/** Kurallar servis icinde: ayar alanlari birbirine ve kataloga bagli. */
record CreateReportRequest(String name, ReportConfig config) {

	CreateReportRequest {
		name = Texts.trim(name);
	}

}
