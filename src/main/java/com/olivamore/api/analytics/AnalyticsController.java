package com.olivamore.api.analytics;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code AnalyticsController}: analiz ekrani ve rapor olusturucu ({@code analytics.view}). Hepsi
 * {@code ?from=Y-m-d&to=Y-m-d} ister, en fazla iki yil.
 */
@RestController
@RequiresPermission("analytics.view")
class AnalyticsController {

	private final Db db;
	private final LaravelValidator validator;
	private final Analytics analytics;

	AnalyticsController(Db db, LaravelValidator validator, Analytics analytics) {
		this.db = db;
		this.validator = validator;
		this.analytics = analytics;
	}

	/** {@code ?product_type_id=}, {@code ?supplier_id=} ile daraltilabilir. */
	@GetMapping("/api/analytics/yield")
	Map<String, Object> yield(RequestInput input) {
		Analytics.Range range = range(input);
		ValidatedInput f = validator.validate(input, Rules.of()
			.field("product_type_id", "nullable|integer|exists:production_product_types,id")
			.field("supplier_id", "nullable|integer|exists:suppliers,id"));
		return Map.of("data", analytics.yield(range, nullableLong(f.get("product_type_id")), nullableLong(f.get("supplier_id"))));
	}

	@GetMapping("/api/analytics/suppliers")
	Map<String, Object> suppliers(RequestInput input) {
		Analytics.Range range = range(input);
		ValidatedInput f = validator.validate(input, Rules.of()
			.field("product_type_id", "nullable|integer|exists:production_product_types,id"));
		return Map.of("data", analytics.suppliers(range, nullableLong(f.get("product_type_id"))));
	}

	@GetMapping("/api/analytics/customers")
	Map<String, Object> customers(RequestInput input) {
		return Map.of("data", analytics.customers(range(input)));
	}

	@GetMapping("/api/analytics/customers/{customer}")
	Map<String, Object> customer(@Bound(value = "customer", table = "customers", model = "Customer") Row customer, RequestInput input) {
		return Map.of("data", analytics.customer(customer, range(input)));
	}

	// ---- rapor olusturucu ---------------------------------------------------------------

	@GetMapping("/api/analytics/catalog")
	Map<String, Object> catalog() {
		return Map.of("data", ReportSpec.catalog());
	}

	/** {@code ?metric=&group=&chart=} ve kaynaga uyan filtreler; tutarsiz secim 422. */
	@GetMapping("/api/analytics/query")
	Map<String, Object> query(RequestInput input) {
		Analytics.Range range = range(input);
		ValidatedInput spec = validator.validate(input, Rules.of()
			.field("metric", "required|in:" + String.join(",", ReportSpec.METRICS.keySet()))
			.field("group", "required|in:" + String.join(",", ReportSpec.GROUPS.keySet()))
			.field("chart", "nullable|in:" + String.join(",", ReportSpec.CHARTS.keySet()))
			.field("supplier_id", "nullable|integer")
			.field("supplier_field_id", "nullable|integer")
			.field("product_type_id", "nullable|integer")
			.field("customer_id", "nullable|integer")
			.field("finished_product_id", "nullable|integer"));
		String metric = Php.strval(spec.get("metric"));
		String group = Php.strval(spec.get("group"));
		String error = ReportSpec.specError(metric, group, spec.get("chart") == null ? null : Php.strval(spec.get("chart")));
		if (error != null) {
			throw new HttpStatusException(422, error);
		}
		Map<String, Object> filters = new LinkedHashMap<>();
		for (List<String> keys : ReportSpec.FILTERS.values()) {
			for (String key : keys) {
				if (spec.get(key) != null) {
					filters.put(key, spec.get(key));
				}
			}
		}
		return Map.of("data", ReportSpec.query(analytics, metric, group, range, filters));
	}

	// ---- kayitli raporlar (kisiye ozel) ----------------------------------------------------

	@GetMapping("/api/analytics/reports")
	Map<String, Object> reports(CurrentUser user) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row r : db.select("select id, name, config, created_at, updated_at from analytics_reports where user_id = ?"
			+ " order by updated_at desc", user.id())) {
			out.add(report(r));
		}
		return Map.of("data", out);
	}

	@PostMapping("/api/analytics/reports")
	@Transactional
	ResponseEntity<Map<String, Object>> storeReport(RequestInput input, CurrentUser user) {
		ValidatedInput v = validateReport(input, "required");
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("user_id", user.id());
		values.put("name", v.get("name"));
		values.put("config", Db.json(v.get("config")));
		Row created = db.create("analytics_reports", values);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", report(created)));
	}

	/** Baskasinin raporu yokmus gibi (404). */
	@PatchMapping("/api/analytics/reports/{report}")
	@Transactional
	Map<String, Object> updateReport(@Bound(value = "report", table = "analytics_reports", model = "AnalyticsReport") Row report,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validateReport(input, "sometimes");
		assertOwner(report, user);
		Map<String, Object> changes = new LinkedHashMap<>();
		if (v.has("name")) {
			changes.put("name", v.get("name"));
		}
		if (v.has("config")) {
			changes.put("config", Db.json(v.get("config")));
		}
		db.update("analytics_reports", report.id(), changes);
		return Map.of("data", report(db.find("analytics_reports", report.id()).orElseThrow()));
	}

	@DeleteMapping("/api/analytics/reports/{report}")
	@Transactional
	Map<String, String> destroyReport(@Bound(value = "report", table = "analytics_reports", model = "AnalyticsReport") Row report,
		CurrentUser user) {
		assertOwner(report, user);
		db.delete("analytics_reports", report.id());
		return Map.of("message", "Rapor silindi.");
	}

	// ---- yardimcilar ------------------------------------------------------------------------

	/** {@code SaveAnalyticsReportRequest}: ayar kurallari yalnizca {@code config} gonderildiyse; sonra secim tutarliligi. */
	private ValidatedInput validateReport(RequestInput input, String presence) {
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
		ValidatedInput v = validator.validate(input, rules);
		// after(): kurallar gectiyse olcu/gruplama/grafik uyumu
		if (input.get("config") instanceof Map<?, ?> config) {
			String error = ReportSpec.specError(Php.strval(config.get("metric")), Php.strval(config.get("group")),
				config.get("chart") == null ? null : Php.strval(config.get("chart")));
			if (error != null) {
				throw LaravelValidationException.of("config", error);
			}
		}
		return v;
	}

	private static Map<String, Object> report(Row r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", r.get("id"));
		m.put("name", r.get("name"));
		m.put("config", r.get("config") instanceof Db.Json j ? j.value() : r.get("config"));
		m.put("created_at", r.get("created_at"));
		m.put("updated_at", r.get("updated_at"));
		return m;
	}

	private static void assertOwner(Row report, CurrentUser user) {
		if (Php.intval(report.get("user_id")) != user.id()) {
			throw new HttpStatusException(404, "");
		}
	}

	/** {@code from}/{@code to} (Y-m-d, to >= from), en fazla iki yil (Carbon: 731 gunden fazla fark). */
	private Analytics.Range range(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("from", "required|date_format:Y-m-d")
			.field("to", "required|date_format:Y-m-d|after_or_equal:from"));
		Analytics.Range range = new Analytics.Range(LocalDate.parse(v.string("from")), LocalDate.parse(v.string("to")));
		if (range.inclusiveDays() - 1 >= 731) {
			throw new HttpStatusException(422, "Aralık en fazla iki yıl olabilir.");
		}
		return range;
	}

	private static Long nullableLong(Object value) {
		return value == null ? null : Php.intval(value);
	}

}
