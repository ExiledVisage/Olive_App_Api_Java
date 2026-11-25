package com.olivamore.api.quality;

import com.olivamore.api.shared.web.DataResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** BRC/IFS hazirlik paneli (docs/QUALITY.md 4). */
@RestController
class ReadinessController {

	private final Readiness readiness;

	ReadinessController(Readiness readiness) {
		this.readiness = readiness;
	}

	@GetMapping("/api/quality/readiness")
	@PreAuthorize("hasAuthority('quality.view')")
	DataResponse<Readiness.Panel> panel() {
		return DataResponse.of(readiness.panel());
	}

}
