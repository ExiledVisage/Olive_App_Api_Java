package com.olivamore.api.analytics.report;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

record ReportResponse(Long id, String name, JsonNode config, LocalDateTime createdAt, LocalDateTime updatedAt) {

	static ReportResponse of(AnalyticsReport report) {
		return new ReportResponse(report.getId(), report.getName(), report.getConfig(), report.getCreatedAt(),
			report.getUpdatedAt());
	}

}
