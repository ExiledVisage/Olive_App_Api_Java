package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.storagemovement.StorageMovementListing;
import com.olivamore.api.inventory.storagemovement.StorageMovementResponse;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Tanklar, bakiyeler, hareketler, izinli urun/tip, attribute kurallari. */
@RestController
class StorageUnitController {

	private final StorageUnitService units;
	private final StorageUnitResponses responses;
	private final StorageMovementListing movements;

	StorageUnitController(StorageUnitService units, StorageUnitResponses responses,
		StorageMovementListing movements) {
		this.units = units;
		this.responses = responses;
		this.movements = movements;
	}

	// ---- okuma (storage_unit.view) -----------------------------------------

	@GetMapping("/api/storage-units")
	@PreAuthorize("hasAuthority('storage_unit.view')")
	ListResponse<StorageUnitResponse> index() {
		return new ListResponse<>(responses.all());
	}

	@GetMapping("/api/storage-units/{id}")
	@PreAuthorize("hasAuthority('storage_unit.view')")
	DataResponse<StorageUnitResponse> show(@PathVariable long id) {
		units.find(id);
		return DataResponse.of(responses.one(id));
	}

	/** En yeni once (moved_at desc, id desc); {@code page}/{@code size} varsa sayfali. */
	@GetMapping("/api/storage-units/{id}/movements")
	@PreAuthorize("hasAuthority('storage_unit.view')")
	PagedModel<StorageMovementResponse> movements(@PathVariable long id, Pageable pageable) {
		units.find(id);
		return movements.forUnit(id, pageable);
	}

	@GetMapping("/api/storage-units/{id}/balances")
	@PreAuthorize("hasAuthority('storage_unit.view')")
	ListResponse<StorageUnitResponse.Balance> balances(@PathVariable long id) {
		units.find(id);
		return new ListResponse<>(responses.balances(id));
	}

	// ---- yazma (storage_unit.manage) ---------------------------------------

	/** Cevap 201. */
	@PostMapping("/api/storage-units")
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	ResponseEntity<DataResponse<StorageUnitResponse>> store(@Valid @RequestBody CreateStorageUnitRequest request) {
		return DataResponse.created(responses.one(units.create(request)));
	}

	@RequestMapping(path = "/api/storage-units/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	DataResponse<StorageUnitResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateStorageUnitRequest request) {
		units.update(id, request);
		return DataResponse.of(responses.one(id));
	}

	/** Cevap 204. */
	@DeleteMapping("/api/storage-units/{id}")
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	ResponseEntity<Void> destroy(@PathVariable long id) {
		units.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/api/storage-units/{id}/allowed-production-products")
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	DataResponse<StorageUnitResponse> syncAllowedProducts(@PathVariable long id,
		@Valid @RequestBody SyncAllowedProductsRequest request) {
		units.syncAllowedProducts(id, request);
		return DataResponse.of(responses.one(id));
	}

	@PutMapping("/api/storage-units/{id}/allowed-production-product-types")
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	DataResponse<StorageUnitResponse> syncAllowedTypes(@PathVariable long id,
		@Valid @RequestBody SyncAllowedTypesRequest request) {
		units.syncAllowedTypes(id, request);
		return DataResponse.of(responses.one(id));
	}

	/** Listede olmayan kurallar silinir, digerleri eklenir ya da guncellenir; hata olursa hepsi geri alinir. */
	@PutMapping("/api/storage-units/{id}/attribute-rules")
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	DataResponse<StorageUnitResponse> syncAttributeRules(@PathVariable long id,
		@Valid @RequestBody SyncAttributeRulesRequest request) {
		units.syncAttributeRules(id, request);
		return DataResponse.of(responses.one(id));
	}

}
