package com.olivamore.api.inventory;

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
	private final StorageMovements movements;
	private final InventoryReads reads;

	StorageMovementController(Db db, LaravelValidator validator, StorageMovements movements, InventoryReads reads) {
		this.db = db;
		this.validator = validator;
		this.movements = movements;
		this.reads = reads;
	}

	@PostMapping("/api/storage-movements/opening")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<Map<String, Object>>> opening(RequestInput input) {
		ValidatedInput v = validator.validate(input, attributeValueRules(Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("production_product_type_id", "required|integer|exists:production_product_types,id")
			.field("quantity", "required|numeric|gt:0")
			.field("unit", "required|string|in:KG,LITRE")
			.field("attribute_values", "nullable|array"))
			.field("note", "nullable|string"));
		Row unit = find("storage_units", "StorageUnit", v.get("storage_unit_id"));
		Row type = find("production_product_types", "ProductionProductType", v.get("production_product_type_id"));
		return created(movements.opening(unit, type, Php.floatval(v.get("quantity")), v.string("unit"),
			items(v.get("attribute_values")), v.string("note")));
	}

	/** material_id ile var olan materyal, production_product_type_id ile yeni materyal. */
	@PostMapping("/api/storage-movements/inbound")
	@RequiresPermission("storage_movement.create")
	ResponseEntity<DataResponse<Map<String, Object>>> inbound(RequestInput input) {
		ValidatedInput v = validator.validate(input, attributeValueRules(Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("material_id", "required_without:production_product_type_id|nullable|integer|exists:materials,id")
			.field("production_product_type_id",
				"required_without:material_id|nullable|integer|exists:production_product_types,id|prohibits:material_id")
			.field("unit", "nullable|string|in:KG,LITRE|prohibits:material_id")
			.field("attribute_values", "nullable|array|prohibits:material_id"))
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("note", "nullable|string"));
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
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("material_id", "required|integer|exists:materials,id")
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("note", "nullable|string"));
		Row unit = find("storage_units", "StorageUnit", v.get("storage_unit_id"));
		Row material = find("materials", "Material", v.get("material_id"));
		Row batchProcess = batchProcess(v.get("batch_process_id"));
		return created(movements.outbound(unit, material, Php.floatval(v.get("quantity")), batchProcess, v.string("note")));
	}

	@PostMapping("/api/storage-movements/transfer")
	@RequiresPermission("storage_movement.transfer")
	DataResponse<Map<String, Object>> transfer(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("from_storage_unit_id", "required|integer|exists:storage_units,id")
			.field("to_storage_unit_id", "required|integer|exists:storage_units,id|different:from_storage_unit_id")
			.field("material_id", "required|integer|exists:materials,id")
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("note", "nullable|string"));
		Row from = find("storage_units", "StorageUnit", v.get("from_storage_unit_id"));
		Row to = find("storage_units", "StorageUnit", v.get("to_storage_unit_id"));
		Row material = find("materials", "Material", v.get("material_id"));
		Row batchProcess = batchProcess(v.get("batch_process_id"));
		StorageMovements.Transfer t = movements.transfer(from, to, material, Php.floatval(v.get("quantity")), batchProcess,
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
		ValidatedInput v = validator.validate(input, attributeValueRules(Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("is_increase", "required|boolean")
			.field("production_product_type_id", (increase ? "required|" : "") + "nullable|integer|exists:production_product_types,id")
			.field("unit", (increase ? "required|" : "") + "nullable|string|in:KG,LITRE")
			.field("material_id", (increase ? "" : "required|") + "nullable|integer|exists:materials,id")
			.field("attribute_values", "nullable|array"))
			.field("note", "nullable|string"));
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
		StringBuilder where = new StringBuilder(" from storage_movements where storage_unit_id = ?");
		List<Object> args = new ArrayList<>(List.of(unit.id()));
		String from = request.getParameter("date_from");
		String to = request.getParameter("date_to");
		// when($dateFrom): PHP'de "0" de false
		if (from != null && !from.equals("0")) {
			where.append(" and moved_at::date >= ?");
			args.add(from);
		}
		if (to != null && !to.equals("0")) {
			where.append(" and moved_at::date <= ?");
			args.add(to);
		}
		String order = " order by moved_at desc, id desc";
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(reads.movements(db.select("select *" + where + order, args.toArray())));
		}
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate" + where, args.toArray());
		List<Row> rows = total == 0 ? List.of()
			: db.select("select *" + where + order + " limit " + perPage.get() + " offset " + (long) (page - 1) * perPage.get(), args.toArray());
		return LaravelPaginator.paginate(reads.movements(rows), total, page, perPage.get(), request);
	}

	// ---- yardimcilar -------------------------------------------------------

	private static Rules attributeValueRules(Rules rules) {
		return rules
			.field("attribute_values.*.production_product_attribute_definition_id",
				"required|integer|exists:production_product_attribute_definitions,id")
			.field("attribute_values.*.value_boolean", "nullable|boolean")
			.field("attribute_values.*.value_integer", "nullable|integer")
			.field("attribute_values.*.value_decimal", "nullable|numeric")
			.field("attribute_values.*.value_text", "nullable|string")
			.field("attribute_values.*.value_json", "nullable|array");
	}

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
