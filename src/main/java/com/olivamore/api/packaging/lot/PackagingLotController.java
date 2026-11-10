package com.olivamore.api.packaging.lot;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Lotlar, mal girisi, kunye duzeltmesi, sayim duzeltmesi, silme. */
@RestController
class PackagingLotController {

	private final PackagingLotService service;
	private final PackagingLotResponses responses;

	PackagingLotController(PackagingLotService service, PackagingLotResponses responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping("/api/packaging-lots")
	@PreAuthorize("hasAuthority('packaging_lot.view')")
	ListResponse<PackagingLotResponse> index(
		@RequestParam(name = "packaging_item_id", required = false) Long itemId,
		@RequestParam(name = "product_supplier_id", required = false) Long supplierId,
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String search) {
		return new ListResponse.Plain<>(responses.list(itemId, supplierId, status, search));
	}

	@GetMapping("/api/packaging-items/{itemId}/lots")
	@PreAuthorize("hasAuthority('packaging_lot.view')")
	ListResponse<PackagingLotResponse> indexForItem(@PathVariable long itemId) {
		service.ensureItemExists(itemId);
		return new ListResponse.Plain<>(responses.list(itemId, null, null, null));
	}

	@GetMapping("/api/packaging-lots/{id}")
	@PreAuthorize("hasAuthority('packaging_lot.view')")
	DataResponse<PackagingLotResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	@PostMapping("/api/packaging-items/{itemId}/lots")
	@PreAuthorize("hasAuthority('packaging_lot.receive')")
	ResponseEntity<DataResponse<PackagingLotResponse>> store(@PathVariable long itemId,
		@Valid @RequestBody CreatePackagingLotRequest request, @AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(responses.detail(service.receive(itemId, request, user.id())));
	}

	@PatchMapping("/api/packaging-lots/{id}")
	@PreAuthorize("hasAuthority('packaging_lot.receive')")
	DataResponse<PackagingLotResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdatePackagingLotRequest request) {
		service.update(id, request);
		return DataResponse.of(responses.detail(id));
	}

	@DeleteMapping("/api/packaging-lots/{id}")
	@PreAuthorize("hasAuthority('packaging_lot.receive')")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Ambalaj lotu silindi.");
	}

	@PostMapping("/api/packaging-lots/{id}/adjustment")
	@PreAuthorize("hasAuthority('packaging_lot.adjustment')")
	DataResponse<PackagingLotResponse> adjust(@PathVariable long id,
		@Valid @RequestBody AdjustPackagingLotRequest request, @AuthenticationPrincipal CurrentUser user) {
		service.adjust(id, request, user.id());
		return DataResponse.of(responses.detail(id));
	}

}
