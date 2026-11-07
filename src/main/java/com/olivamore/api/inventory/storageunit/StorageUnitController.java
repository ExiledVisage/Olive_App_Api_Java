package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.inventory.storagemovement.StorageMovementResource;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Laravel {@code StorageUnitController}: tanklar, bakiyeler, hareketler, izinli urun/tip, attribute kurallari. */
@RestController
class StorageUnitController {

	private static final String TABLE = "storage_units";
	private static final String MODEL = "StorageUnit";

	private final LaravelValidator validator;
	private final StorageUnitService units;
	private final StorageUnitResource resource;
	private final StorageMovementResource movementResource;

	StorageUnitController(LaravelValidator validator, StorageUnitService units, StorageUnitResource resource,
		StorageMovementResource movementResource) {
		this.validator = validator;
		this.units = units;
		this.resource = resource;
		this.movementResource = movementResource;
	}

	// ---- okuma (storage_unit.view) -----------------------------------------

	@GetMapping("/api/storage-units")
	@RequiresPermission("storage_unit.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(resource.storageUnits(units.all()));
	}

	@GetMapping("/api/storage-units/{storage_unit}")
	@RequiresPermission("storage_unit.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "storage_unit", table = TABLE, model = MODEL) Row unit) {
		return DataResponse.of(resource.storageUnit(unit));
	}

	/** {@code movements()} en yeni once (moved_at desc, id desc); {@code per_page} varsa sayfali. */
	@GetMapping("/api/storage-units/{storageUnit}/movements")
	@RequiresPermission("storage_unit.view")
	ListResponse<Map<String, Object>> movements(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(movementResource.movements(units.movements(unit.id(), null, null)));
		}
		int page = Listing.currentPage(request);
		long total = units.movementCount(unit.id());
		List<Row> rows = total == 0 ? List.of() : units.movements(unit.id(), page, perPage.get());
		return LaravelPaginator.paginate(movementResource.movements(rows), total, page, perPage.get(), request);
	}

	@GetMapping("/api/storage-units/{storageUnit}/balances")
	@RequiresPermission("storage_unit.view")
	ListResponse<Map<String, Object>> balances(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit) {
		return new ListResponse.Plain<>(resource.balances(units.balances(unit.id())));
	}

	// ---- yazma (storage_unit.manage) ---------------------------------------

	/** Cevap 201. */
	@PostMapping("/api/storage-units")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageUnitRequests.store());
		return DataResponse.created(resource.storageUnit(units.create(v)));
	}

	@RequestMapping(path = "/api/storage-units/{storage_unit}", method = {RequestMethod.PATCH, RequestMethod.PUT})
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "storage_unit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageUnitRequests.update(unit.id()));
		units.update(unit, v);
		return DataResponse.of(resource.storageUnit(units.byId(unit.id())));
	}

	/** Cevap 204. */
	@DeleteMapping("/api/storage-units/{storage_unit}")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	ResponseEntity<Void> destroy(@Bound(value = "storage_unit", table = TABLE, model = MODEL) Row unit) {
		units.delete(unit);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/api/storage-units/{storageUnit}/allowed-production-products")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> syncAllowedProducts(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageUnitRequests.syncAllowedProducts());
		units.syncAllowedProducts(unit, v.get("production_product_ids"));
		return DataResponse.of(resource.storageUnit(unit));
	}

	@PutMapping("/api/storage-units/{storageUnit}/allowed-production-product-types")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> syncAllowedTypes(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageUnitRequests.syncAllowedTypes());
		units.syncAllowedTypes(unit, v.get("production_product_type_ids"));
		return DataResponse.of(resource.storageUnit(unit));
	}

	/** Listede olmayan kurallar silinir, digerleri updateOrCreate; hata olursa hepsi geri alinir. */
	@PutMapping("/api/storage-units/{storageUnit}/attribute-rules")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> syncAttributeRules(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageUnitRequests.syncAttributeRules());
		units.syncAttributeRules(unit, v);
		return DataResponse.of(resource.storageUnit(unit));
	}

}
