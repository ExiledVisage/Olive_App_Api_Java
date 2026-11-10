package com.olivamore.api.finishedproduct.storage;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Bitmis urun depolari, raflar, depoya yerlestirme, transfer, cikis ve hareket gecmisi. */
@RestController
class FinishedProductStorageController {

	private final FinishedProductStorageService storages;
	private final FinishedProductStorageResponses responses;

	FinishedProductStorageController(FinishedProductStorageService storages,
		FinishedProductStorageResponses responses) {
		this.storages = storages;
		this.responses = responses;
	}

	// ---- okuma ----------------------------------------------------------------------

	@GetMapping("/api/finished-product-storages")
	@PreAuthorize("hasAuthority('finished_product_storage.view')")
	ListResponse<StorageResponse> index() {
		return new ListResponse.Plain<>(responses.list());
	}

	@GetMapping("/api/finished-product-storages/{id}")
	@PreAuthorize("hasAuthority('finished_product_storage.view')")
	DataResponse<StorageResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	/** Hareket gecmisi, yeniden eskiye. */
	@GetMapping("/api/finished-product-storages/{id}/movements")
	@PreAuthorize("hasAuthority('finished_product_storage.view')")
	ListResponse<MovementResponse> movements(@PathVariable long id) {
		return new ListResponse.Plain<>(responses.movements(id));
	}

	// ---- depo -----------------------------------------------------------------------

	/** 201; raflar ve stok yuklenmez. */
	@PostMapping("/api/finished-product-storages")
	@PreAuthorize("hasAuthority('finished_product_storage.manage')")
	ResponseEntity<DataResponse<StorageResponse>> store(@Valid @RequestBody CreateStorageRequest request) {
		return DataResponse.created(responses.bare(storages.create(request)));
	}

	@RequestMapping(path = "/api/finished-product-storages/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@PreAuthorize("hasAuthority('finished_product_storage.manage')")
	DataResponse<StorageResponse> update(@PathVariable long id, @Valid @RequestBody UpdateStorageRequest request) {
		storages.update(id, request);
		return DataResponse.of(responses.detail(id));
	}

	@DeleteMapping("/api/finished-product-storages/{id}")
	@PreAuthorize("hasAuthority('finished_product_storage.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		storages.delete(id);
		return Map.of("message", "Depo silindi.");
	}

	// ---- raf ------------------------------------------------------------------------

	/** 201. */
	@PostMapping("/api/finished-product-storages/{id}/slots")
	@PreAuthorize("hasAuthority('finished_product_storage.manage')")
	ResponseEntity<DataResponse<SlotResponse>> storeSlot(@PathVariable long id,
		@Valid @RequestBody CreateSlotRequest request) {
		return DataResponse.created(responses.slot(storages.createSlot(id, request)));
	}

	/** Raf baska bir depoya ait olsa da kabul edilir. */
	@PatchMapping("/api/finished-product-storages/{id}/slots/{slot}")
	@PreAuthorize("hasAuthority('finished_product_storage.manage')")
	DataResponse<SlotResponse> updateSlot(@PathVariable long id, @PathVariable long slot,
		@Valid @RequestBody UpdateSlotRequest request) {
		storages.updateSlot(id, slot, request);
		return DataResponse.of(responses.slot(slot));
	}

	@DeleteMapping("/api/finished-product-storages/{id}/slots/{slot}")
	@PreAuthorize("hasAuthority('finished_product_storage.manage')")
	Map<String, String> destroySlot(@PathVariable long id, @PathVariable long slot) {
		storages.deleteSlot(id, slot);
		return Map.of("message", "Slot silindi.");
	}

	// ---- stok -----------------------------------------------------------------------

	/** Depoya yerlestirme: tek raf ya da {@code placements}. */
	@PostMapping("/api/finished-product-storages/{id}/stock-in")
	@PreAuthorize("hasAuthority('finished_product_storage.stock_in')")
	DataResponse<StorageResponse> stockIn(@PathVariable long id, @Valid @RequestBody StockInRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		storages.stockIn(id, request, user.id());
		return DataResponse.of(responses.detail(id));
	}

	/** Raftan rafa; cevapta iki depo. */
	@PostMapping("/api/finished-product-storages/{id}/transfer")
	@PreAuthorize("hasAuthority('finished_product_storage.stock_in')")
	DataResponse<TransferResponse> transfer(@PathVariable long id, @Valid @RequestBody TransferRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		long target = storages.transfer(id, request, user.id());
		return DataResponse.of(new TransferResponse(responses.detail(id), responses.detail(target)));
	}

	@PostMapping("/api/finished-product-storages/{id}/stock-out")
	@PreAuthorize("hasAuthority('finished_product_storage.stock_out')")
	DataResponse<StorageResponse> stockOut(@PathVariable long id, @Valid @RequestBody StockOutRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		storages.stockOut(id, request, user.id());
		return DataResponse.of(responses.detail(id));
	}

}
