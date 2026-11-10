package com.olivamore.api.analytics.dailysummary;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Permissions;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.json.FlexibleDateTimeDeserializer;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Acilis ekrani. Ayri izin istemez; her bolum kullanicinin iznine bakar, izin yoksa govdede yer almaz. */
@RestController
class DailySummaryController {

	private final Permissions permissions;
	private final DailySummaryService summaries;

	DailySummaryController(Permissions permissions, DailySummaryService summaries) {
		this.permissions = permissions;
		this.summaries = summaries;
	}

	/** {@code ?date=} (verilmezse bugun). */
	@GetMapping("/api/daily-summary")
	DataResponse<DailySummaryResponse> show(@RequestParam(required = false) String date,
		@AuthenticationPrincipal CurrentUser user) {
		LocalDate day = date == null ? Timestamps.now().toLocalDate() : parse(date);
		return DataResponse.of(new DailySummaryResponse(day.toString(),
			permissions.can(user, "production_batch.view") ? summaries.production(day) : null,
			permissions.can(user, "batch_process.view") ? summaries.stages(day) : null,
			permissions.can(user, "finished_product_run.view") ? summaries.fillingRuns(day) : null,
			permissions.can(user, "order.view") ? summaries.orders(day) : null,
			permissions.can(user, "finished_product.view") ? summaries.stock() : null));
	}

	private static LocalDate parse(String date) {
		return FlexibleDateTimeDeserializer.parse(date).map(LocalDateTime::toLocalDate)
			.orElseThrow(() -> LaravelValidationException.of("date", LaravelMessages.format(LaravelMessages.DATE, "date")));
	}

}
