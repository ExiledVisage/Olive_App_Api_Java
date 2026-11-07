package com.olivamore.api.analytics.report;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code AnalyticsController}'in kayitli raporlari (kisiye ozel; baskasinin raporu 404). */
@RestController
@RequiresPermission("analytics.view")
class AnalyticsReportController {

	private final Db db;
	private final LaravelValidator validator;

	AnalyticsReportController(Db db, LaravelValidator validator) {
		this.db = db;
		this.validator = validator;
	}

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

	private ValidatedInput validateReport(RequestInput input, String presence) {
		ValidatedInput v = validator.validate(input, AnalyticsReportRequests.save(input, presence));
		AnalyticsReportRequests.after(input);
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

}
