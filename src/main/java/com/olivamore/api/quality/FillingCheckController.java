package com.olivamore.api.quality;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Dolum kosusunun hat hazirligi ve saatlik kontrolleri (docs/QUALITY.md 2). */
@RestController
class FillingCheckController {

	private final FillingChecks checks;

	FillingCheckController(FillingChecks checks) {
		this.checks = checks;
	}

	@GetMapping("/api/finished-product-runs/{runId}/filling-checks")
	@PreAuthorize("hasAnyAuthority('finished_product_run.view', 'filling_check.record', 'quality.view')")
	DataResponse<FillingChecks.RunChecks> index(@PathVariable long runId) {
		return DataResponse.of(checks.of(runId));
	}

	@PutMapping("/api/finished-product-runs/{runId}/filling-setup")
	@PreAuthorize("hasAuthority('filling_check.record')")
	DataResponse<FillingChecks.RunChecks> setup(@PathVariable long runId,
		@RequestBody FillingChecks.SetupRequest request, @AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(checks.setup(runId, request, user.id()));
	}

	@PostMapping("/api/finished-product-runs/{runId}/filling-checks")
	@PreAuthorize("hasAuthority('filling_check.record')")
	DataResponse<FillingChecks.RunChecks> store(@PathVariable long runId,
		@RequestBody FillingChecks.CheckRequest request, @AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(checks.record(runId, request, user.id()));
	}

	/** Yanlis girilen kontrol; kalite yoneticisi siler. */
	@DeleteMapping("/api/filling-checks/{id}")
	@PreAuthorize("hasAuthority('quality.manage')")
	DataResponse<FillingChecks.RunChecks> destroy(@PathVariable long id) {
		return DataResponse.of(checks.delete(id));
	}

}
