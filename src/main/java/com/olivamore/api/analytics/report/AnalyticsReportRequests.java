package com.olivamore.api.analytics.report;

import com.olivamore.api.analytics.analytics.ReportSpec;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.web.RequestInput;

import java.util.Map;

/**
 * Laravel {@code SaveAnalyticsReportRequest}: ad ve ayar (olcu, gruplama, grafik, donem, filtreler). Ayar kurallari
 * yalnizca {@code config} gonderildiyse; kurallar gecince olcu/gruplama/grafik uyumu ({@code after()}).
 */
final class AnalyticsReportRequests {

	private AnalyticsReportRequests() {
	}

	/** @param presence ekleme {@code required}, guncelleme {@code sometimes} */
	static Rules save(RequestInput input, String presence) {
		Rules rules = Rules.of()
			.field("name", presence + "|string|max:120")
			.field("config", presence + "|array");
		if (input.has("config")) {
			rules.field("config.metric", "required_with:config|in:" + String.join(",", ReportSpec.METRICS.keySet()))
				.field("config.group", "required_with:config|in:" + String.join(",", ReportSpec.GROUPS.keySet()))
				.field("config.chart", "required_with:config|in:" + String.join(",", ReportSpec.CHARTS.keySet()))
				.field("config.period", "required_with:config|array")
				.field("config.period.preset", "nullable|in:week,month,quarter,year")
				.field("config.period.from", "required_without:config.period.preset|nullable|date_format:Y-m-d")
				.field("config.period.to", "required_without:config.period.preset|nullable|date_format:Y-m-d|after_or_equal:config.period.from")
				.field("config.filters", "nullable|array")
				.field("config.filters.*", "nullable|integer");
		}
		return rules;
	}

	/** {@code after()}: secimin tutarliligi; tutarsizsa 422 ({@code config}). */
	static void after(RequestInput input) {
		// after(): kurallar gectiyse olcu/gruplama/grafik uyumu
		if (input.get("config") instanceof Map<?, ?> config) {
			String error = ReportSpec.specError(Php.strval(config.get("metric")), Php.strval(config.get("group")),
				config.get("chart") == null ? null : Php.strval(config.get("chart")));
			if (error != null) {
				throw LaravelValidationException.of("config", error);
			}
		}
	}

}
