package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.inventory.storageunit.StorageUnitRepository;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Acilis, giris, cikis, transfer, duzeltme ve tankin hareket gecmisi. Hareket cevaplari 201, transfer 200. */
@RestController
class StorageMovementController {

	private final StorageMovementService movements;
	private final StorageMovementResponses responses;
	private final StorageMovementListing listing;
	private final StorageUnitRepository units;

	StorageMovementController(StorageMovementService movements, StorageMovementResponses responses,
		StorageMovementListing listing, StorageUnitRepository units) {
		this.movements = movements;
		this.responses = responses;
		this.listing = listing;
		this.units = units;
	}

	@PostMapping("/api/storage-movements/opening")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<StorageMovementResponse>> opening(@Valid @RequestBody OpeningRequest request) {
		return created(movements.opening(request));
	}

	/** {@code material_id} ile var olan materyal, {@code production_product_type_id} ile yeni materyal. */
	@PostMapping("/api/storage-movements/inbound")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<StorageMovementResponse>> inbound(@Valid @RequestBody InboundRequest request) {
		return created(movements.inbound(request));
	}

	@PostMapping("/api/storage-movements/outbound")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<StorageMovementResponse>> outbound(@Valid @RequestBody OutboundRequest request) {
		return created(movements.outbound(request));
	}

	@PostMapping("/api/storage-movements/transfer")
	@RequiresPermission("storage_movement.transfer")
	DataResponse<StorageMovementResponse.Transfer> transfer(@Valid @RequestBody TransferRequest request) {
		StorageMovementService.Transfer transfer = movements.transfer(request);
		List<StorageMovementResponse> both = responses.written(List.of(transfer.out(), transfer.in()));
		return DataResponse.of(new StorageMovementResponse.Transfer(transfer.reference(), both.get(0), both.get(1)));
	}

	@PostMapping("/api/storage-movements/adjustment")
	@RequiresPermission("storage_movement.adjustment")
	ResponseEntity<DataResponse<StorageMovementResponse>> adjustment(@Valid @RequestBody AdjustmentRequest request) {
		return created(movements.adjustment(request));
	}

	/** {@code ?date_from}, {@code ?date_to}: moved_at araligi; {@code per_page} varsa sayfali. */
	@GetMapping("/api/storage-units/{id}/movement-history")
	@RequiresPermission("storage_movement.view")
	ListResponse<StorageMovementResponse> history(@PathVariable long id,
		@RequestParam(name = "date_from", required = false) String dateFrom,
		@RequestParam(name = "date_to", required = false) String dateTo, HttpServletRequest request) {
		if (!units.existsById(id)) {
			throw new ModelNotFoundException("StorageUnit", id);
		}
		return listing.history(id, dateFrom, dateTo, request);
	}

	private ResponseEntity<DataResponse<StorageMovementResponse>> created(StorageMovement movement) {
		return DataResponse.created(responses.written(List.of(movement)).getFirst());
	}

}
