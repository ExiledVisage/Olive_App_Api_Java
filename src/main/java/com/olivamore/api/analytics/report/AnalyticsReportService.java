package com.olivamore.api.analytics.report;

import com.olivamore.api.analytics.analytics.Range;
import com.olivamore.api.analytics.analytics.ReportSpec;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import com.olivamore.api.shared.web.PhpNumbers;
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

	private static final String REQUIRED_WITH_CONFIG = "The :attribute field is required when config is present.";

	private static final String REQUIRED_WITHOUT_PRESET =
		"The :attribute field is required when config.period.preset is not present.";

	private static final String AFTER_FROM = "The :attribute field must be a date after or equal to config.period.from.";

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
			errors.add("name", LaravelMessages.REQUIRED);
		}
		if (request.config() == null) {
			errors.add("config", LaravelMessages.REQUIRED);
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
			errors.add("name", LaravelMessages.maxString(MAX_NAME_LENGTH));
		}
		if (config != null) {
			checkConfig(errors, config);
		}
		errors.throwIfAny();
		if (config != null) {
			String error = ReportSpec.specError(config.metric(), config.group(), config.chart());
			if (error != null) {
				throw LaravelValidationException.of("config", error);
			}
		}
	}

	private static void checkConfig(ValidationErrors errors, ReportConfig config) {
		choice(errors, "config.metric", config.metric(), ReportSpec::isMetric);
		choice(errors, "config.group", config.group(), ReportSpec::isGroup);
		choice(errors, "config.chart", config.chart(), ReportSpec::isChart);
		ReportConfig.Period period = config.period() == null ? new ReportConfig.Period(null, null, null) : config.period();
		if (period.preset() == null && period.from() == null && period.to() == null) {
			errors.add("config.period", REQUIRED_WITH_CONFIG);
		}
		if (period.preset() != null && !PRESETS.contains(period.preset())) {
			errors.add("config.period.preset", LaravelMessages.IN);
		}
		String format = LaravelMessages.dateFormat("Y-m-d");
		if (period.from() == null && period.preset() == null) {
			errors.add("config.period.from", REQUIRED_WITHOUT_PRESET);
		}
		else if (period.from() != null && Range.ymd(period.from()).isEmpty()) {
			errors.add("config.period.from", format);
		}
		if (period.to() == null) {
			if (period.preset() == null) {
				errors.add("config.period.to", REQUIRED_WITHOUT_PRESET);
			}
		}
		else {
			if (Range.ymd(period.to()).isEmpty()) {
				errors.add("config.period.to", format);
			}
			if (!Range.inOrder(period.from(), period.to())) {
				errors.add("config.period.to", AFTER_FROM);
			}
		}
		if (config.filters() != null) {
			config.filters().forEach((key, value) -> {
				if (value != null && !value.isNull() && !isInteger(value)) {
					String path = "config.filters." + key;
					errors.add(path, LaravelMessages.INTEGER.replace(":attribute", path));
				}
			});
		}
	}

	private static void choice(ValidationErrors errors, String field, String value, Predicate<String> allowed) {
		if (value == null) {
			errors.add(field, REQUIRED_WITH_CONFIG);
		}
		else if (!allowed.test(value)) {
			errors.add(field, LaravelMessages.IN);
		}
	}

	private static boolean isInteger(JsonNode value) {
		if (value.isString()) {
			return PhpNumbers.validateInt(value.asString()).isPresent();
		}
		return value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0;
	}

}
