package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;

/**
 * Laravel {@code FinishedProductStorageController}: bitmis urun depolari, raflar, depoya yerlestirme, transfer,
 * cikis ve hareket gecmisi. (Siparise toplama ve iade {@code ShelfOperations} ile siparis modulunden.)
 */
@RestController
class FinishedProductStorageController {

	private static final String STORAGE = "finished_product_storages";

	private final Db db;
	private final LaravelValidator validator;
	private final FinishedProductStorageService storages;
	private final FinishedProductStorageResource resource;

	FinishedProductStorageController(Db db, LaravelValidator validator, FinishedProductStorageService storages,
		FinishedProductStorageResource resource) {
		this.db = db;
		this.validator = validator;
		this.storages = storages;
		this.resource = resource;
	}

	// ---- okuma (finished_product_storage.view) --------------------------------------

	@GetMapping("/api/finished-product-storages")
	@RequiresPermission("finished_product_storage.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(resource.storages(storages.all()));
	}

	@GetMapping("/api/finished-product-storages/{storage}")
	@RequiresPermission("finished_product_storage.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage) {
		return DataResponse.of(resource.storage(storage.id()));
	}

	/** Hareket gecmisi, yeniden eskiye. */
	@GetMapping("/api/finished-product-storages/{storage}/movements")
	@RequiresPermission("finished_product_storage.view")
	ListResponse<Map<String, Object>> movements(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage) {
		return new ListResponse.Plain<>(resource.movements(storage.id()));
	}

	// ---- depo (finished_product_storage.manage) ---------------------------------------

	/** 201; iliskiler yuklenmez ({@code slots}, {@code stock} yok). */
	@PostMapping("/api/finished-product-storages")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductStorageRequests.store());
		return DataResponse.created(FinishedProductStorageResource.bare(storages.create(v.all())));
	}

	@RequestMapping(path = "/api/finished-product-storages/{storage}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductStorageRequests.update());
		storages.update(storage, v.all());
		return DataResponse.of(resource.storage(storage.id()));
	}

	@DeleteMapping("/api/finished-product-storages/{storage}")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage) {
		storages.delete(storage);
		return Map.of("message", "Depo silindi.");
	}

	// ---- raf (finished_product_storage.manage) ----------------------------------------

	/** 201; hacim decimal:4 cast'inden gecmis haliyle ({@code (float)}). */
	@PostMapping("/api/finished-product-storages/{storage}/slots")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeSlot(
		@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage, RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductStorageRequests.storeSlot());
		Row stored = storages.slot(storages.createSlot(storage.id(), v.all()).id());
		Map<Long, Row> products = stored.longValue("finished_product_id") == null ? Map.of()
			: Relations.byId(db.whereIn("finished_products", "id", List.of(stored.longValue("finished_product_id")), null));
		return DataResponse.created(FinishedProductStorageResource.slot(stored, stored.get("volume_litre"), List.of(), products));
	}

	/** Raf baska bir depoya ait olsa da kabul edilir (Laravel'de kapsamli binding yok). */
	@PatchMapping("/api/finished-product-storages/{storage}/slots/{slot}")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateSlot(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		@Bound(value = "slot", table = "finished_product_storage_slots", model = "FinishedProductStorageSlot") Row slot, RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductStorageRequests.updateSlot());
		storages.updateSlot(slot.id(), v.all());
		return DataResponse.of(resource.slots(List.of(storages.slot(slot.id()))).getFirst());
	}

	@DeleteMapping("/api/finished-product-storages/{storage}/slots/{slot}")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	Map<String, String> destroySlot(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		@Bound(value = "slot", table = "finished_product_storage_slots", model = "FinishedProductStorageSlot") Row slot) {
		storages.deleteSlot(slot);
		return Map.of("message", "Slot silindi.");
	}

	// ---- stok --------------------------------------------------------------------------

	/** Depoya yerlestirme: tek raf ya da {@code placements}. */
	@PostMapping("/api/finished-product-storages/{storage}/stock-in")
	@RequiresPermission("finished_product_storage.stock_in")
	@Transactional
	DataResponse<Map<String, Object>> stockIn(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, FinishedProductStorageRequests.stockIn());
		storages.stockIn(storage, v.all(), user.id());
		return DataResponse.of(resource.storage(storage.id()));
	}

	/** Raftan rafa; cevapta iki depo. */
	@PostMapping("/api/finished-product-storages/{storage}/transfer")
	@RequiresPermission("finished_product_storage.stock_in")
	@Transactional
	Map<String, Object> transfer(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, FinishedProductStorageRequests.transfer());
		long target = storages.transfer(storage, v.all(), user.id());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("source_storage", resource.storage(storage.id()));
		data.put("target_storage", resource.storage(target));
		return Map.of("data", data);
	}

	@PostMapping("/api/finished-product-storages/{storage}/stock-out")
	@RequiresPermission("finished_product_storage.stock_out")
	@Transactional
	DataResponse<Map<String, Object>> stockOut(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, FinishedProductStorageRequests.stockOut());
		storages.stockOut(storage, v.all(), user.id());
		return DataResponse.of(resource.storage(storage.id()));
	}

}
