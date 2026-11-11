package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.storageunit.StorageUnitRepository;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Acilis, giris, cikis, transfer, duzeltme ve tankin hareket gecmisi. Hareket cevaplari 201, transfer 200; cikis,
 * transfer ve duzeltme son tankta karisimdan oransal oldugu icin hareket listesi doner.
 */
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
	@PreAuthorize("hasAuthority('storage_movement.create')")
	ResponseEntity<DataResponse<StorageMovementResponse>> opening(@Valid @RequestBody OpeningRequest request) {
		return created(movements.opening(request));
	}

	/** {@code material_id} ile var olan materyal, {@code production_product_type_id} ile yeni materyal. */
	@PostMapping("/api/storage-movements/inbound")
	@PreAuthorize("hasAuthority('storage_movement.create')")
	ResponseEntity<DataResponse<StorageMovementResponse>> inbound(@Valid @RequestBody InboundRequest request) {
		return created(movements.inbound(request));
	}

	/** Son tankta karisimdaki her materyal icin bir hareket: cevap liste. */
	@PostMapping("/api/storage-movements/outbound")
	@PreAuthorize("hasAuthority('storage_movement.create')")
	ResponseEntity<DataResponse<List<StorageMovementResponse>>> outbound(@Valid @RequestBody OutboundRequest request) {
		return DataResponse.created(responses.written(movements.outbound(request)));
	}

	@PostMapping("/api/storage-movements/transfer")
	@PreAuthorize("hasAuthority('storage_movement.transfer')")
	DataResponse<StorageMovementResponse.Transfer> transfer(@Valid @RequestBody TransferRequest request) {
		StorageMovementService.Transfer transfer = movements.transfer(request);
		return DataResponse.of(new StorageMovementResponse.Transfer(transfer.reference(),
			responses.written(transfer.out()), responses.written(transfer.in())));
	}

	/**
	 * On dinlendirme tankinin bosaltilmasi (Boşalt): onay gerektirmez, tanklar arasi transfer yetkisiyle yapilir.
	 */
	@PostMapping("/api/storage-units/{id}/empty")
	@PreAuthorize("hasAuthority('storage_movement.transfer')")
	DataResponse<StorageMovementResponse.Emptying> empty(@PathVariable long id, @Valid @RequestBody EmptyRequest request) {
		StorageMovementService.Emptying emptying = movements.empty(id, request);
		return DataResponse.of(new StorageMovementResponse.Emptying(emptying.reference(),
			responses.written(emptying.out()), responses.written(emptying.in()), responses.residues(emptying.residueIds())));
	}

	/** Son tankta azalis karisimdaki her materyal icin bir hareket: cevap liste. */
	@PostMapping("/api/storage-movements/adjustment")
	@PreAuthorize("hasAuthority('storage_movement.adjustment')")
	ResponseEntity<DataResponse<List<StorageMovementResponse>>> adjustment(
		@Valid @RequestBody AdjustmentRequest request) {
		return DataResponse.created(responses.written(movements.adjustment(request)));
	}

	/** {@code ?date_from}, {@code ?date_to}: moved_at araligi; {@code page}/{@code size} varsa sayfali. */
	@GetMapping("/api/storage-units/{id}/movement-history")
	@PreAuthorize("hasAuthority('storage_movement.view')")
	PagedModel<StorageMovementResponse> history(@PathVariable long id,
		@RequestParam(name = "date_from", required = false) String dateFrom,
		@RequestParam(name = "date_to", required = false) String dateTo, Pageable pageable) {
		if (!units.existsById(id)) {
			throw new ModelNotFoundException("StorageUnit", id);
		}
		return listing.history(id, dateFrom, dateTo, pageable);
	}

	private ResponseEntity<DataResponse<StorageMovementResponse>> created(StorageMovement movement) {
		return DataResponse.created(responses.written(List.of(movement)).getFirst());
	}

}
