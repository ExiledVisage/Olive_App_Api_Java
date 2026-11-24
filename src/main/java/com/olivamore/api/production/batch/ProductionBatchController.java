package com.olivamore.api.production.batch;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Partiler ve takvim. */
@RestController
class ProductionBatchController {

	private final ProductionBatchService batches;
	private final ProductionBatchResponses responses;
	private final ProductionBatchListing listing;
	private final BatchGroupService groups;

	ProductionBatchController(ProductionBatchService batches, ProductionBatchResponses responses,
		ProductionBatchListing listing, BatchGroupService groups) {
		this.batches = batches;
		this.responses = responses;
		this.listing = listing;
		this.groups = groups;
	}

	/**
	 * {@code ?received_from}, {@code ?received_to} (varis gunu, dahil); {@code ?kind=delivery} teslimatlar,
	 * {@code ?kind=processable} makineli asamalari yurutulen partiler (gruplar ve bolunmeyen hattin teslimatlari);
	 * en yeni once; page/size ile sayfali.
	 */
	@GetMapping("/api/production-batches")
	@PreAuthorize("hasAuthority('production_batch.view')")
	PagedModel<ProductionBatchResponse> index(
		@RequestParam(name = "received_from", required = false) String receivedFrom,
		@RequestParam(name = "received_to", required = false) String receivedTo,
		@RequestParam(required = false) String kind, Pageable pageable) {
		return listing.list(receivedFrom, receivedTo, kind, pageable);
	}

	/** Gun -> parti sayisi (varis gunu); bos aralikta {@code {}}. */
	@GetMapping("/api/production-batches/calendar")
	@PreAuthorize("hasAuthority('production_batch.view')")
	Map<String, Map<String, Long>> calendar(@RequestParam(required = false) String from,
		@RequestParam(required = false) String to) {
		return Map.of("data", listing.calendar(from, to));
	}

	@GetMapping("/api/production-batches/{id}")
	@PreAuthorize("hasAuthority('production_batch.view')")
	DataResponse<ProductionBatchResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	@PostMapping("/api/production-batches")
	@PreAuthorize("hasAuthority('production_batch.create')")
	DataResponse<ProductionBatchResponse> store(@Valid @RequestBody CreateProductionBatchRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(responses.detail(batches.create(request, user)));
	}

	/** Teslimattan grup ayirir; cevap yeni grup. */
	@PostMapping("/api/production-batches/{id}/groups")
	@PreAuthorize("hasAuthority('production_batch.create')")
	DataResponse<ProductionBatchResponse> split(@PathVariable long id, @Valid @RequestBody CreateBatchGroupRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(responses.detail(groups.split(id, request, user)));
	}

	/** Grubun payi tartilan kiloya gore duzeltilir; cevap grup. */
	@PostMapping("/api/production-batches/{id}/allocation")
	@PreAuthorize("hasAuthority('production_batch.update')")
	DataResponse<ProductionBatchResponse> correct(@PathVariable long id,
		@Valid @RequestBody CorrectBatchGroupRequest request) {
		groups.correct(id, request);
		return DataResponse.of(responses.detail(id));
	}

	@PatchMapping("/api/production-batches/{id}")
	@PreAuthorize("hasAuthority('production_batch.update')")
	DataResponse<ProductionBatchResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateProductionBatchRequest request) {
		batches.update(id, request);
		return DataResponse.of(responses.detail(id));
	}

	/** Teslimatin alim bilgisi (kanal, KDV haric kg fiyati, KDV orani, nakliye, not); istek bilginin tamami. */
	@PutMapping("/api/production-batches/{id}/purchase")
	@PreAuthorize("hasAuthority('production_batch.price')")
	DataResponse<ProductionBatchResponse> purchase(@PathVariable long id,
		@Valid @RequestBody UpdatePurchaseRequest request) {
		batches.purchase(id, request);
		return DataResponse.of(responses.detail(id));
	}

	/** Cevap 204. */
	@DeleteMapping("/api/production-batches/{id}")
	@PreAuthorize("hasAuthority('production_batch.delete')")
	ResponseEntity<Void> destroy(@PathVariable long id) {
		batches.delete(id);
		return ResponseEntity.noContent().build();
	}

}
