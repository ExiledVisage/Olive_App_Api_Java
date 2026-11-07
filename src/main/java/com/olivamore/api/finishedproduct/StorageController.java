package com.olivamore.api.finishedproduct;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code FinishedProductStorageController}: bitmis urun depolari, raflar, depoya yerlestirme, transfer,
 * cikis ve hareket gecmisi. (Siparise toplama ve iade Faz 9'da.)
 */
@RestController
class StorageController {

	private static final String STORAGE = "finished_product_storages";

	private final Db db;
	private final LaravelValidator validator;
	private final StorageFlow flow;
	private final StorageReads reads;

	StorageController(Db db, LaravelValidator validator, StorageFlow flow, StorageReads reads) {
		this.db = db;
		this.validator = validator;
		this.flow = flow;
		this.reads = reads;
	}

	// ---- okuma (finished_product_storage.view) --------------------------------------

	@GetMapping("/api/finished-product-storages")
	@RequiresPermission("finished_product_storage.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(reads.storages(db.select("select * from " + STORAGE)));
	}

	@GetMapping("/api/finished-product-storages/{storage}")
	@RequiresPermission("finished_product_storage.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage) {
		return DataResponse.of(reads.storage(storage.id()));
	}

	/** Hareket gecmisi, yeniden eskiye. */
	@GetMapping("/api/finished-product-storages/{storage}/movements")
	@RequiresPermission("finished_product_storage.view")
	ListResponse<Map<String, Object>> movements(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage) {
		return new ListResponse.Plain<>(reads.movements(storage.id()));
	}

	// ---- depo (finished_product_storage.manage) ---------------------------------------

	/** 201; iliskiler yuklenmez ({@code slots}, {@code stock} yok). */
	@PostMapping("/api/finished-product-storages")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255")
			.field("description", "nullable|string")
			.field("is_active", "boolean"));
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("description", v.get("description"));
		values.put("is_active", v.get("is_active") == null ? true : v.get("is_active"));
		return DataResponse.created(StorageReads.bare(db.create(STORAGE, values)));
	}

	@RequestMapping(path = "/api/finished-product-storages/{storage}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("description", "sometimes|nullable|string")
			.field("is_active", "sometimes|boolean"));
		if (!v.all().isEmpty()) {
			db.update(STORAGE, storage.id(), v.all());
		}
		return DataResponse.of(reads.storage(storage.id()));
	}

	/**
	 * Stoklu depo silinmez (Laravel mesaji). Stogu bosalmis ama hareket gecmisi olan depo Laravel'de restrict FK
	 * yuzunden 500 veriyordu; burada 422 (DEVIATIONS 15).
	 */
	@DeleteMapping("/api/finished-product-storages/{storage}")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage) {
		if (db.count("select count(*) from finished_product_storage_stocks where finished_product_storage_id = ? and quantity > ?",
			storage.id(), 0) > 0) {
			throw new BusinessRuleException("Bu depoda stok bulunduğu için silinemez.");
		}
		DeletionGuard.of("Bu depo")
			.usedBy("stok hareketi", db.count("select count(*) from finished_product_stock_movements where finished_product_storage_id = ?",
				storage.id()))
			.check("Pasife alabilirsiniz.");
		db.delete(STORAGE, storage.id());
		return Map.of("message", "Depo silindi.");
	}

	// ---- raf (finished_product_storage.manage) ----------------------------------------

	/** 201; hacim decimal:4 cast'inden gecmis haliyle ({@code (float)}). */
	@PostMapping("/api/finished-product-storages/{storage}/slots")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeSlot(
		@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "nullable|string|max:50")
			.field("volume_litre", "required|numeric|min:0.001")
			.field("max_quantity", "required|integer|min:1")
			.field("finished_product_id", "nullable|integer|exists:finished_products,id"));
		Row slot = flow.createSlot(storage.id(), v.all());
		Row stored = db.find("finished_product_storage_slots", slot.id()).orElseThrow();
		Map<Long, Row> products = stored.longValue("finished_product_id") == null ? Map.of()
			: Relations.byId(db.whereIn("finished_products", "id", List.of(stored.longValue("finished_product_id")), null));
		return DataResponse.created(StorageReads.slot(stored, stored.get("volume_litre"), List.of(), products));
	}

	/** Raf baska bir depoya ait olsa da kabul edilir (Laravel'de kapsamli binding yok). */
	@PatchMapping("/api/finished-product-storages/{storage}/slots/{slot}")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateSlot(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		@Bound(value = "slot", table = "finished_product_storage_slots", model = "FinishedProductStorageSlot") Row slot, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|nullable|string|max:50")
			.field("volume_litre", "sometimes|numeric|min:0.001")
			.field("max_quantity", "sometimes|integer|min:1")
			.field("finished_product_id", "sometimes|nullable|integer|exists:finished_products,id"));
		flow.updateSlot(slot.id(), v.all());
		return DataResponse.of(reads.slots(List.of(db.find("finished_product_storage_slots", slot.id()).orElseThrow())).getFirst());
	}

	/** Dolu raf silinmez (Laravel mesaji); hareket gecmisi olan raf Laravel'de 500, burada 422 (DEVIATIONS 15). */
	@DeleteMapping("/api/finished-product-storages/{storage}/slots/{slot}")
	@RequiresPermission("finished_product_storage.manage")
	@Transactional
	Map<String, String> destroySlot(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		@Bound(value = "slot", table = "finished_product_storage_slots", model = "FinishedProductStorageSlot") Row slot) {
		if (Php.intval(slot.get("current_quantity")) > 0) {
			throw new BusinessRuleException("Bu slotta ürün bulunduğu için silinemez.");
		}
		DeletionGuard.of("Bu slot")
			.usedBy("stok hareketi", db.count("select count(*) from finished_product_stock_movements where finished_product_storage_slot_id = ?",
				slot.id()))
			.check();
		db.delete("finished_product_storage_slots", slot.id());
		return Map.of("message", "Slot silindi.");
	}

	// ---- stok --------------------------------------------------------------------------

	/** Depoya yerlestirme: tek raf ({@code finished_product_storage_slot_id} + {@code quantity}) ya da {@code placements}. */
	@PostMapping("/api/finished-product-storages/{storage}/stock-in")
	@RequiresPermission("finished_product_storage.stock_in")
	@Transactional
	DataResponse<Map<String, Object>> stockIn(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("finished_product_run_id", "nullable|integer|exists:finished_product_runs,id")
			.field("finished_product_storage_slot_id", "required_without:placements|integer|exists:finished_product_storage_slots,id")
			.field("quantity", "required_without:placements|integer|min:1")
			.field("placements", "required_without:finished_product_storage_slot_id|array|min:1")
			.field("placements.*.finished_product_storage_slot_id", "required_with:placements|integer|exists:finished_product_storage_slots,id")
			.field("placements.*.quantity", "required_with:placements|integer|min:1")
			.field("note", "nullable|string|max:1000")
			.message("placements.min", "En az bir rafa miktar girilmelidir.")
			.message("placements.*.quantity.min", "Raf miktarı en az 1 olmalıdır."));
		flow.stockIn(storage, v.all(), user.id());
		return DataResponse.of(reads.storage(storage.id()));
	}

	/** Raftan rafa; cevapta iki depo. */
	@PostMapping("/api/finished-product-storages/{storage}/transfer")
	@RequiresPermission("finished_product_storage.stock_in")
	@Transactional
	Map<String, Object> transfer(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("source_slot_id", "required|integer|exists:finished_product_storage_slots,id")
			.field("target_slot_id", "required|integer|exists:finished_product_storage_slots,id|different:source_slot_id")
			.field("quantity", "required|integer|min:1"));
		long target = flow.transfer(storage, v.all(), user.id());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("source_storage", reads.storage(storage.id()));
		data.put("target_storage", reads.storage(target));
		return Map.of("data", data);
	}

	@PostMapping("/api/finished-product-storages/{storage}/stock-out")
	@RequiresPermission("finished_product_storage.stock_out")
	@Transactional
	DataResponse<Map<String, Object>> stockOut(@Bound(value = "storage", table = STORAGE, model = "FinishedProductStorage") Row storage,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("finished_product_storage_slot_id", "required|integer|exists:finished_product_storage_slots,id")
			.field("quantity", "required|integer|min:1"));
		flow.stockOut(storage, v.all(), user.id());
		return DataResponse.of(reads.storage(storage.id()));
	}

}
