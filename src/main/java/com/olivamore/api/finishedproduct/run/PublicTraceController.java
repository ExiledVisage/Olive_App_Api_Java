package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.throttle.Throttle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Sisedeki QR: kosuya herkese acik bag (giris gerekir) ve QR'in actigi sayfa (giris gerekmez, adres basina
 * dakikada 30 istek). Sayfa HTML.
 */
@RestController
class PublicTraceController {

	private static final String NOT_FOUND = """
		<!doctype html><html lang="tr"><head><meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1"><title>Bulunamadı</title></head>
		<body style="font-family:system-ui,sans-serif;padding:24px">Bu QR koduna ait bir kayıt yok.</body></html>
		""";

	private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

	private final PublicTraceService traces;

	PublicTraceController(PublicTraceService traces) {
		this.traces = traces;
	}

	record Link(String url) {
	}

	/** Bag yoksa acilir, varsa aynisi; {@code ?regenerate=true} yeni anahtar (eski QR gecersiz). */
	@PostMapping("/api/finished-product-runs/{id}/public-link")
	@PreAuthorize("hasAuthority('finished_product_run.complete')")
	DataResponse<Link> link(@PathVariable long id,
		@RequestParam(name = "regenerate", defaultValue = "false") boolean regenerate) {
		return DataResponse.of(new Link(traces.link(id, regenerate)));
	}

	/** Cevap 204. */
	@DeleteMapping("/api/finished-product-runs/{id}/public-link")
	@PreAuthorize("hasAuthority('finished_product_run.complete')")
	ResponseEntity<Void> revoke(@PathVariable long id) {
		traces.revoke(id);
		return ResponseEntity.noContent().build();
	}

	/** Istemcinin Accept basligindan bagimsiz HTML (QR'i okutan her tarayici). */
	@GetMapping("/api/public/runs/{token}")
	@Throttle(maxAttempts = 30, decayMinutes = 1)
	ResponseEntity<String> page(@PathVariable String token) {
		return traces.page(token)
			.map(html -> ResponseEntity.ok().contentType(HTML).body(html))
			.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML).body(NOT_FOUND));
	}

}
