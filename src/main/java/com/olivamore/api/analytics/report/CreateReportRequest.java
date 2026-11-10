package com.olivamore.api.analytics.report;

/** Kurallar servis icinde: ayar alanlari birbirine ve kataloga bagli. */
record CreateReportRequest(String name, ReportConfig config) {
}
