package com.olivamore.api.integration.catalog;

import com.olivamore.api.integration.sync.SyncRuns;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Urun eslestirme (docs/INTEGRATIONS.md 0). */
@RestController
class CatalogController {

	private final Catalog catalog;

	CatalogController(Catalog catalog) {
		this.catalog = catalog;
	}

	/** Platformdaki urunleri ceker ve kendiliginden eslestirir. */
	@PostMapping("/api/integrations/connections/{id}/catalog/refresh")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<Catalog.Refresh> refresh(@PathVariable long id) {
		return DataResponse.of(catalog.refresh(id, SyncRuns.Trigger.MANUAL));
	}

	/** {@code status}: matched, unmatched, ignored; verilmezse hepsi. */
	@GetMapping("/api/integrations/connections/{id}/catalog")
	@PreAuthorize("hasAuthority('integration.view')")
	ListResponse<Catalog.Link> links(@PathVariable long id,
		@RequestParam(name = "status", required = false) String status) {
		return new ListResponse<>(catalog.links(id, status));
	}

	/** Sitede karsiligi olmayan (hicbir satira bagli olmayan) aktif urunlerimiz. */
	@GetMapping("/api/integrations/connections/{id}/catalog/unlinked-products")
	@PreAuthorize("hasAuthority('integration.view')")
	ListResponse<Catalog.Product> unlinked(@PathVariable long id) {
		return new ListResponse<>(catalog.unlinkedProducts(id));
	}

	/** Elle bagla; {@code finished_product_id} bos ise "eslestirilmesin". */
	@PutMapping("/api/integrations/catalog-links/{linkId}")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<Catalog.Link> link(@PathVariable long linkId, @RequestBody Catalog.LinkRequest request) {
		return DataResponse.of(catalog.link(linkId, request));
	}

	/** Elle baglamayi ya da "eslestirilmesin"i kaldirir. */
	@DeleteMapping("/api/integrations/catalog-links/{linkId}")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<Catalog.Link> reset(@PathVariable long linkId) {
		return DataResponse.of(catalog.reset(linkId));
	}

}
