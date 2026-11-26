package com.olivamore.api.integration.sync;

import com.olivamore.api.shared.web.ListResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Senkron gecmisi (docs/INTEGRATIONS.md 0). */
@RestController
class SyncController {

	private final SyncRuns runs;

	SyncController(SyncRuns runs) {
		this.runs = runs;
	}

	/** En yeni once; varsayilan 50, en fazla 200. */
	@GetMapping("/api/integrations/connections/{id}/runs")
	@PreAuthorize("hasAuthority('integration.view')")
	ListResponse<SyncRuns.Run> recent(@PathVariable long id,
		@RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {
		return new ListResponse<>(runs.recent(id, Math.max(1, Math.min(limit, 200))));
	}

}
