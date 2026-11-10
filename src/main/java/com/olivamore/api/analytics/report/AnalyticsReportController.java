package com.olivamore.api.analytics.report;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Kisiye ozel kayitli raporlar. */
@RestController
@PreAuthorize("hasAuthority('analytics.view')")
class AnalyticsReportController {

	private final AnalyticsReportService reports;

	AnalyticsReportController(AnalyticsReportService reports) {
		this.reports = reports;
	}

	@GetMapping("/api/analytics/reports")
	DataResponse<List<ReportResponse>> reports(@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(reports.list(user.id()));
	}

	@PostMapping("/api/analytics/reports")
	ResponseEntity<DataResponse<ReportResponse>> store(@RequestBody CreateReportRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(reports.create(request, user.id()));
	}

	@PatchMapping("/api/analytics/reports/{id}")
	DataResponse<ReportResponse> update(@PathVariable long id, @RequestBody UpdateReportRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(reports.update(id, request, user.id()));
	}

	@DeleteMapping("/api/analytics/reports/{id}")
	Map<String, String> destroy(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
		reports.delete(id, user.id());
		return Map.of("message", "Rapor silindi.");
	}

}
