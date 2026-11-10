package com.olivamore.api.production.batch;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

	ProductionBatchController(ProductionBatchService batches, ProductionBatchResponses responses,
		ProductionBatchListing listing) {
		this.batches = batches;
		this.responses = responses;
		this.listing = listing;
	}

	/** {@code ?received_from}, {@code ?received_to} (varis gunu, dahil); en yeni once; per_page ile sayfali. */
	@GetMapping("/api/production-batches")
	@RequiresPermission("production_batch.view")
	ListResponse<ProductionBatchResponse> index(
		@RequestParam(name = "received_from", required = false) String receivedFrom,
		@RequestParam(name = "received_to", required = false) String receivedTo, HttpServletRequest request) {
		return listing.list(receivedFrom, receivedTo, request);
	}

	/** Gun -> parti sayisi (varis gunu); bos aralikta {@code {}}. */
	@GetMapping("/api/production-batches/calendar")
	@RequiresPermission("production_batch.view")
	Map<String, Map<String, Long>> calendar(@RequestParam(required = false) String from,
		@RequestParam(required = false) String to) {
		return Map.of("data", listing.calendar(from, to));
	}

	@GetMapping("/api/production-batches/{id}")
	@RequiresPermission("production_batch.view")
	DataResponse<ProductionBatchResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	@PostMapping("/api/production-batches")
	@RequiresPermission("production_batch.create")
	DataResponse<ProductionBatchResponse> store(@Valid @RequestBody CreateProductionBatchRequest request, CurrentUser user) {
		return DataResponse.of(responses.detail(batches.create(request, user)));
	}

	@PatchMapping("/api/production-batches/{id}")
	@RequiresPermission("production_batch.update")
	DataResponse<ProductionBatchResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateProductionBatchRequest request) {
		batches.update(id, request);
		return DataResponse.of(responses.detail(id));
	}

	/** Cevap 204. */
	@DeleteMapping("/api/production-batches/{id}")
	@RequiresPermission("production_batch.delete")
	ResponseEntity<Void> destroy(@PathVariable long id) {
		batches.delete(id);
		return ResponseEntity.noContent().build();
	}

}
