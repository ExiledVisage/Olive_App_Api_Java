package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Laravel {@code StorageMovementController}: acilis, giris, cikis, transfer, duzeltme ve tankin hareket
 * gecmisi. Hareket cevaplari 201 (yeni model), transfer 200 (StorageTransferResource bir model degil).
 */
@RestController
class StorageMovementController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");

	private final Db db;
	private final LaravelValidator validator;
	private final StorageMovementService movements;
	private final StorageMovementResource reads;

	StorageMovementController(Db db, LaravelValidator validator, StorageMovementService movements, StorageMovementResource reads) {
		this.db = db;
		this.validator = validator;
		this.movements = movements;
		this.reads = reads;
	}

	@PostMapping("/api/storage-movements/opening")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<Map<String, Object>>> opening(RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageMovementRequests.opening());
		Row unit = find("storage_units", "StorageUnit", v.get("storage_unit_id"));
		Row type = find("production_product_types", "ProductionProductType", v.get("production_product_type_id"));
		return created(movements.opening(unit, type, Php.floatval(v.get("quantity")), v.string("unit"),
			items(v.get("attribute_values")), v.string("note")));
	}

	/** material_id ile var olan materyal, production_product_type_id ile yeni materyal. */
	@PostMapping("/api/storage-movements/inbound")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<Map<String, Object>>> inbound(RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageMovementRequests.inbound());
		Row unit = find("storage_units", "StorageUnit", v.get("storage_unit_id"));
		Row batchProcess = batchProcess(v.get("batch_process_id"));
		double quantity = Php.floatval(v.get("quantity"));
		if (notEmpty(v.get("material_id"))) {
			return created(movements.inbound(unit, find("materials", "Material", v.get("material_id")), quantity, batchProcess,
				v.string("note")));
		}
		Row type = find("production_product_types", "ProductionProductType", v.get("production_product_type_id"));
		return created(movements.inboundFromType(unit, type, quantity, v.string("unit"), items(v.get("attribute_values")),
			batchProcess, v.string("note")));
	}

	@PostMapping("/api/storage-movements/outbound")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<Map<String, Object>>> outbound(RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageMovementRequests.outbound());
		Row unit = find("storage_units", "StorageUnit", v.get("storage_unit_id"));
		Row material = find("materials", "Material", v.get("material_id"));
		Row batchProcess = batchProcess(v.get("batch_process_id"));
		return created(movements.outbound(unit, material, Php.floatval(v.get("quantity")), batchProcess, v.string("note")));
	}

	@PostMapping("/api/storage-movements/transfer")
	@RequiresPermission("storage_movement.transfer")
	DataResponse<Map<String, Object>> transfer(RequestInput input) {
		ValidatedInput v = validator.validate(input, StorageMovementRequests.transfer());
		Row from = find("storage_units", "StorageUnit", v.get("from_storage_unit_id"));
		Row to = find("storage_units", "StorageUnit", v.get("to_storage_unit_id"));
		Row material = find("materials", "Material", v.get("material_id"));
		Row batchProcess = batchProcess(v.get("batch_process_id"));
		StorageMovementService.Transfer t = movements.transfer(from, to, material, Php.floatval(v.get("quantity")), batchProcess,
			v.string("note"));
		List<Map<String, Object>> both = reads.movements(List.of(t.out(), t.in()), true);
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("transfer_reference", t.reference());
		r.put("out_movement", both.get(0));
		r.put("in_movement", both.get(1));
		return DataResponse.of(r);
	}

	/** is_increase: artis yeni materyal (tip + birim zorunlu), azalis var olan materyalden (material_id zorunlu). */
	@PostMapping("/api/storage-movements/adjustment")
	@RequiresPermission("storage_movement.adjustment")
	ResponseEntity<DataResponse<Map<String, Object>>> adjustment(RequestInput input) {
		// Rule::requiredIf($this->boolean('is_increase')): ham girdi filter_var(BOOLEAN) ile
		boolean increase = filterBoolean(input.get("is_increase"));
		ValidatedInput v = validator.validate(input, StorageMovementRequests.adjustment(increase));
		Row unit = find("storage_units", "StorageUnit", v.get("storage_unit_id"));
		Row batchProcess = batchProcess(v.get("batch_process_id"));
		double quantity = Php.floatval(v.get("quantity"));
		if (phpBool(v.get("is_increase"))) {
			Row type = find("production_product_types", "ProductionProductType", v.get("production_product_type_id"));
			return created(movements.adjustmentIncrease(unit, type, quantity, v.string("unit"), items(v.get("attribute_values")),
				batchProcess, v.string("note")));
		}
		Row material = find("materials", "Material", v.get("material_id"));
		return created(movements.adjustmentDecrease(unit, material, quantity, batchProcess, v.string("note")));
	}

	/** ?date_from, ?date_to: moved_at::date araligi; per_page varsa sayfali. */
	@GetMapping("/api/storage-units/{storageUnit}/movement-history")
	@RequiresPermission("storage_movement.view")
	ListResponse<Map<String, Object>> history(@Bound(value = "storageUnit", table = "storage_units", model = "StorageUnit") Row unit,
		HttpServletRequest request) {
		StorageMovementService.History h = movements.history(unit.id(), request.getParameter("date_from"), request.getParameter("date_to"));
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(reads.movements(movements.history(h, null, null)));
		}
		int page = Listing.currentPage(request);
		long total = movements.historyCount(h);
		List<Row> rows = total == 0 ? List.of() : movements.history(h, page, perPage.get());
		return LaravelPaginator.paginate(reads.movements(rows), total, page, perPage.get(), request);
	}

	// ---- yardimcilar -------------------------------------------------------

	private ResponseEntity<DataResponse<Map<String, Object>>> created(Row movement) {
		return DataResponse.created(reads.movements(List.of(movement), true).getFirst());
	}

	/** {@code findOrFail}: validation'dan (exists) sonra; yine de yoksa 404. */
	private Row find(String table, String model, Object id) {
		return db.find(table, Php.intval(id)).orElseThrow(() -> new ModelNotFoundException(model, Php.strval(id)));
	}

	/** resolveBatchProcess: bos (0 dahil) ise yok. */
	private Row batchProcess(Object id) {
		if (id == null || Php.intval(id) == 0) {
			return null;
		}
		return find("batch_processes", "BatchProcess", id);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
		List<Map<String, Object>> out = new ArrayList<>();
		Iterable<?> source = value instanceof List<?> l ? l : value instanceof Map<?, ?> m ? m.values() : List.of();
		for (Object item : source) {
			out.add(item instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
		}
		return out;
	}

	/** PHP {@code !empty($v)} */
	private static boolean notEmpty(Object value) {
		return value != null && !(value instanceof String s && (s.isEmpty() || s.equals("0")))
			&& !(value instanceof Number n && n.doubleValue() == 0) && !Boolean.FALSE.equals(value);
	}

	/** PHP {@code (bool)} (dogrulanmis boolean degeri: true/false/0/1/"0"/"1"). */
	private static boolean phpBool(Object value) {
		return notEmpty(value);
	}

	/** {@code $request->boolean()}: filter_var(FILTER_VALIDATE_BOOLEAN). */
	private static boolean filterBoolean(Object value) {
		if (value instanceof Boolean b) {
			return b;
		}
		if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
			return false;
		}
		return TRUE_VALUES.contains(Php.strval(value).trim().toLowerCase(Locale.ROOT));
	}

}
