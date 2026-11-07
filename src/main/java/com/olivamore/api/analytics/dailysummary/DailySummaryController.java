package com.olivamore.api.analytics.dailysummary;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Permissions;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.RequestInput;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Laravel {@code DailySummaryController}: acilis ekrani. Ayri izin istemez; her bolum kullanicinin iznine bakar,
 * izin yoksa govdede hic yer almaz.
 */
@RestController
class DailySummaryController {

	private final LaravelValidator validator;
	private final Permissions permissions;
	private final DailySummaryService summaries;

	DailySummaryController(LaravelValidator validator, Permissions permissions, DailySummaryService summaries) {
		this.validator = validator;
		this.permissions = permissions;
		this.summaries = summaries;
	}

	/** {@code ?date=} (verilmezse bugun). */
	@GetMapping("/api/daily-summary")
	Map<String, Object> show(RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of().field("date", "nullable|date"));
		LocalDate date = v.get("date") == null ? Timestamps.now().toLocalDate()
			: PhpDates.parse(Php.strval(v.get("date"))).orElseThrow().toLocalDate();
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("date", date.toString());
		if (permissions.can(user, "production_batch.view")) {
			summary.put("production", summaries.production(date));
		}
		if (permissions.can(user, "batch_process.view")) {
			summary.put("stages", summaries.stages(date));
		}
		if (permissions.can(user, "finished_product_run.view")) {
			summary.put("filling_runs", summaries.fillingRuns(date));
		}
		if (permissions.can(user, "order.view")) {
			summary.put("orders", summaries.orders(date));
		}
		if (permissions.can(user, "finished_product.view")) {
			summary.put("stock", summaries.stock());
		}
		return Map.of("data", summary);
	}

}
