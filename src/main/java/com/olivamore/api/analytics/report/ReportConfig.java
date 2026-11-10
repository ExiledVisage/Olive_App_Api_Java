package com.olivamore.api.analytics.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/** Kayitli raporun secimi; gonderilmeyen alanlar saklanmaz, taninmayan alanlar atilir. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ReportConfig(String metric, String group, String chart, Period period, Map<String, JsonNode> filters) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Period(String preset, String from, String to) {
	}

}
