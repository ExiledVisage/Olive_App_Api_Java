package com.olivamore.api.analytics.analytics;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code AnalyticsController}: analiz ekrani ve rapor olusturucu ({@code analytics.view}). Hepsi
 * {@code ?from=Y-m-d&to=Y-m-d} ister, en fazla iki yil. Kayitli raporlar {@code report} klasorunde.
 */
@RestController
@RequiresPermission("analytics.view")
class AnalyticsController {

	private final LaravelValidator validator;
	private final AnalyticsService analytics;

	AnalyticsController(LaravelValidator validator, AnalyticsService analytics) {
		this.validator = validator;
		this.analytics = analytics;
	}

	/** {@code ?product_type_id=}, {@code ?supplier_id=} ile daraltilabilir. */
	@GetMapping("/api/analytics/yield")
	Map<String, Object> yield(RequestInput input) {
		AnalyticsService.Range range = range(input);
		ValidatedInput f = validator.validate(input, AnalyticsRequests.yieldFilters());
		return Map.of("data", analytics.yield(range, nullableLong(f.get("product_type_id")), nullableLong(f.get("supplier_id"))));
	}

	@GetMapping("/api/analytics/suppliers")
	Map<String, Object> suppliers(RequestInput input) {
		AnalyticsService.Range range = range(input);
		ValidatedInput f = validator.validate(input, AnalyticsRequests.supplierFilters());
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

	@GetMapping("/api/analytics/catalog")
	Map<String, Object> catalog() {
		return Map.of("data", ReportSpec.catalog());
	}

	/** {@code ?metric=&group=&chart=} ve kaynaga uyan filtreler; tutarsiz secim 422. */
	@GetMapping("/api/analytics/query")
	Map<String, Object> query(RequestInput input) {
		AnalyticsService.Range range = range(input);
		ValidatedInput spec = validator.validate(input, AnalyticsRequests.query());
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

	// ---- yardimcilar ------------------------------------------------------------------------

	/** {@code from}/{@code to} (Y-m-d, to >= from), en fazla iki yil (Carbon: 731 gunden fazla fark). */
	private AnalyticsService.Range range(RequestInput input) {
		ValidatedInput v = validator.validate(input, AnalyticsRequests.range());
		AnalyticsService.Range range = new AnalyticsService.Range(LocalDate.parse(v.string("from")), LocalDate.parse(v.string("to")));
		if (range.inclusiveDays() - 1 >= 731) {
			throw new HttpStatusException(422, "Aralık en fazla iki yıl olabilir.");
		}
		return range;
	}

	private static Long nullableLong(Object value) {
		return value == null ? null : Php.intval(value);
	}

}
