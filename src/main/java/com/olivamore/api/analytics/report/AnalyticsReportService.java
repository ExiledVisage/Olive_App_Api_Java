package com.olivamore.api.analytics.report;

import com.olivamore.api.analytics.analytics.Range;
import com.olivamore.api.analytics.analytics.ReportSpec;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.function.Predicate;

/** Kullaniciya ozel kayitli raporlar; baskasinin raporu yokmus gibi davranilir. */
@Service
class AnalyticsReportService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final int MAX_NAME_LENGTH = 120;

	private static final List<String> PRESETS = List.of("week", "month", "quarter", "year");

	private final AnalyticsReportRepository reports;

	AnalyticsReportService(AnalyticsReportRepository reports) {
		this.reports = reports;
	}

	@Transactional(readOnly = true)
	List<ReportResponse> list(long userId) {
		return reports.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(ReportResponse::of).toList();
	}

	@Transactional
	ReportResponse create(CreateReportRequest request, long userId) {
		ValidationErrors errors = new ValidationErrors();
		if (request.name() == null) {
			errors.add("name", MessageKeys.REQUIRED);
		}
		if (request.config() == null) {
			errors.add("config", MessageKeys.REQUIRED);
		}
		validate(errors, request.name(), request.config());
		return ReportResponse.of(reports.save(new AnalyticsReport(userId, request.name(), tree(request.config()))));
	}

	@Transactional
	ReportResponse update(long id, UpdateReportRequest request, long userId) {
		AnalyticsReport report = find(id);
		validate(new ValidationErrors(), request.name(), request.config());
		assertOwner(report, userId);
		if (request.name() != null) {
			report.rename(request.name());
		}
		if (request.config() != null) {
			report.configure(tree(request.config()));
		}
		reports.flush();
		return ReportResponse.of(report);
	}

	@Transactional
	void delete(long id, long userId) {
		AnalyticsReport report = find(id);
		assertOwner(report, userId);
		reports.delete(report);
	}

	private AnalyticsReport find(long id) {
		return reports.findById(id).orElseThrow(() -> new ModelNotFoundException("AnalyticsReport", id));
	}

	private static void assertOwner(AnalyticsReport report, long userId) {
		if (report.getUserId() != userId) {
			throw new HttpStatusException(404, "");
		}
	}

	private static JsonNode tree(ReportConfig config) {
		return JSON.valueToTree(config);
	}

	// ---- dogrulama --------------------------------------------------------------------------

	/** Ayar yalnizca gonderildiyse denetlenir; kurallar gecince olcu, gruplama ve grafik uyumu. */
	private static void validate(ValidationErrors errors, String name, ReportConfig config) {
		if (name != null && name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
			errors.add("name", MessageKeys.SIZE_MAX, "max", MAX_NAME_LENGTH);
		}
		if (config != null) {
			checkConfig(errors, config);
		}
		errors.throwIfAny();
		if (config != null) {
			String error = ReportSpec.specError(config.metric(), config.group(), config.chart());
			if (error != null) {
				throw ValidationFailedException.of("config", error);
			}
		}
	}

	private static void checkConfig(ValidationErrors errors, ReportConfig config) {
		choice(errors, "config.metric", config.metric(), ReportSpec::isMetric);
		choice(errors, "config.group", config.group(), ReportSpec::isGroup);
		choice(errors, "config.chart", config.chart(), ReportSpec::isChart);
		ReportConfig.Period period = config.period() == null ? new ReportConfig.Period(null, null, null) : config.period();
		if (period.preset() == null && period.from() == null && period.to() == null) {
			errors.add("config.period", MessageKeys.REQUIRED_WITH, "other", "config");
		}
		if (period.preset() != null && !PRESETS.contains(period.preset())) {
			errors.add("config.period.preset", MessageKeys.IN);
		}
		if (period.from() == null && period.preset() == null) {
			errors.add("config.period.from", MessageKeys.REQUIRED_WITHOUT, "other", "config.period.preset");
		}
		else if (period.from() != null && Range.ymd(period.from()).isEmpty()) {
			errors.add("config.period.from", MessageKeys.DATE_FORMAT, "format", "Y-m-d");
		}
		if (period.to() == null) {
			if (period.preset() == null) {
				errors.add("config.period.to", MessageKeys.REQUIRED_WITHOUT, "other", "config.period.preset");
			}
		}
		else {
			if (Range.ymd(period.to()).isEmpty()) {
				errors.add("config.period.to", MessageKeys.DATE_FORMAT, "format", "Y-m-d");
			}
			if (!Range.inOrder(period.from(), period.to())) {
				errors.add("config.period.to", MessageKeys.AFTER_OR_EQUAL, "other", "config.period.from");
			}
		}
		if (config.filters() != null) {
			config.filters().forEach((key, value) -> {
				if (value != null && !value.isNull() && !isInteger(value)) {
					String path = "config.filters." + key;
					errors.add(path, MessageKeys.INTEGER, "attribute", path);
				}
			});
		}
	}

	private static void choice(ValidationErrors errors, String field, String value, Predicate<String> allowed) {
		if (value == null) {
			errors.add(field, MessageKeys.REQUIRED_WITH, "other", "config");
		}
		else if (!allowed.test(value)) {
			errors.add(field, MessageKeys.IN);
		}
	}

	private static boolean isInteger(JsonNode value) {
		if (value.isString()) {
			try {
				Long.parseLong(value.asString().strip());
				return true;
			}
			catch (NumberFormatException e) {
				return false;
			}
		}
		return value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0;
	}

}
