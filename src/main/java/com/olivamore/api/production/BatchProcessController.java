package com.olivamore.api.production;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Permissions;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code BatchProcessController}: asama detayi, alan degerleri, durum guncellemesi (tamamlama, atlama,
 * geri acma, basarisizlik) ve onay/red. Cevaplar BatchProcessResource (detay iliskileriyle), 200.
 */
@RestController
class BatchProcessController {

	private static final String TABLE = "batch_processes";
	private static final String MODEL = "BatchProcess";
	private static final String FORBIDDEN = "This action is unauthorized.";

	private final Db db;
	private final LaravelValidator validator;
	private final BatchReads reads;
	private final BatchProcessFlow flow;
	private final StageHandlers handlers;
	private final Permissions permissions;

	BatchProcessController(Db db, LaravelValidator validator, BatchReads reads, BatchProcessFlow flow, StageHandlers handlers,
		Permissions permissions) {
		this.db = db;
		this.validator = validator;
		this.reads = reads;
		this.flow = flow;
		this.handlers = handlers;
		this.permissions = permissions;
	}

	@GetMapping("/api/batch-processes/{batchProcess}")
	@RequiresPermission("batch_process.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process) {
		return detail(process.id());
	}

	// ---- alan degerleri ----------------------------------------------------

	/** Degerler alanin veri tipine gore dogrulanir ve ilgili value_* kolonuna yazilir. */
	@PostMapping("/api/batch-processes/{batchProcess}/sync-field-values")
	@RequiresPermission("batch_process.fill")
	@Transactional
	DataResponse<Map<String, Object>> syncFieldValues(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("fields", "required|array|min:1")
			.field("fields.*.process_type_field_id", "required|integer|exists:process_type_fields,id")
			.field("fields.*.value", "nullable"), this::checkFieldValues);
		List<Map<String, Object>> normalized = new ArrayList<>();
		for (Object item : list(v.get("fields"))) {
			Map<String, Object> field = BatchProcessFlow.asMap(item);
			Row definition = db.first("select * from process_type_fields where process_type_fields.id = ? limit 1",
				field.get("process_type_field_id")).orElse(null);
			Object value = field.get("value");
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("process_type_field_id", Php.intval(field.get("process_type_field_id")));
			payload.put("value_text", null);
			payload.put("value_decimal", null);
			payload.put("value_integer", null);
			payload.put("value_boolean", null);
			payload.put("value_datetime", null);
			payload.put("value_json", null);
			if (definition != null && value != null) {
				switch (Php.strval(definition.get("data_type"))) {
					case "decimal" -> payload.put("value_decimal", Php.floatval(value));
					case "integer" -> payload.put("value_integer", Php.intval(value));
					case "boolean" -> payload.put("value_boolean", filterBoolean(value));
					case "datetime" -> payload.put("value_datetime", PhpDates.parse(Php.strval(value)).orElse(null));
					case "json" -> payload.put("value_json", value);
					default -> payload.put("value_text", Php.strval(value));
				}
			}
			normalized.add(payload);
		}
		flow.syncFieldValues(process, normalized);
		return detail(process.id());
	}

	/** {@code withValidator}: deger alanin veri tipine uymali (null serbest). */
	private void checkFieldValues(Map<String, Object> data, java.util.function.BiConsumer<String, String> addError) {
		Object fields = data.get("fields");
		List<?> items = fields instanceof List<?> l ? l : fields instanceof Map<?, ?> m ? new ArrayList<>(m.values()) : List.of();
		for (int index = 0; index < items.size(); index++) {
			// Laravel'de tamsayi olmayan id find()'da SQL hatasi (500) verir; burada kural hatasi (422) yeterli
			if (!(items.get(index) instanceof Map<?, ?> item) || item.get("process_type_field_id") == null
				|| !LaravelValidator.integer(item.get("process_type_field_id"))) {
				continue;
			}
			Row field = db.first("select * from process_type_fields where process_type_fields.id = ? limit 1",
				item.get("process_type_field_id")).orElse(null);
			Object value = item.get("value");
			if (field == null || value == null) {
				continue;
			}
			String path = "fields." + index + ".value";
			switch (Php.strval(field.get("data_type"))) {
				case "decimal" -> {
					if (!LaravelValidator.numeric(value)) {
						addError.accept(path, "The value must be numeric for decimal fields.");
					}
				}
				case "integer" -> {
					if (!LaravelValidator.integer(value)) {
						addError.accept(path, "The value must be an integer.");
					}
				}
				case "boolean" -> {
					if (!(value instanceof Boolean) && !List.of(0L, 1L, "0", "1").contains(value)) {
						addError.accept(path, "The value must be boolean.");
					}
				}
				case "datetime" -> {
					// strtotime yerine ISO bicimleri (DEVIATIONS: tarih dogrulamasi)
					if (!(value instanceof String s && PhpDates.parse(s).isPresent())) {
						addError.accept(path, "The value must be a valid datetime.");
					}
				}
				case "json" -> {
					if (!(value instanceof List<?>) && !(value instanceof Map<?, ?>)) {
						addError.accept(path, "The value must be an array for json fields.");
					}
				}
				default -> {
					if (!(value instanceof String) && !LaravelValidator.numeric(value)) {
						addError.accept(path, "The value must be text.");
					}
				}
			}
		}
	}

	// ---- durum -------------------------------------------------------------

	/**
	 * Tamamlama, atlama ve kapanmis asamayi geri acma ayri yetkiler; dogrulamadan once bakilir (403).
	 * Tamamlanirken {@code operation_data}'nin sekli asamanin uzantilarina gore dogrulanir.
	 */
	@PatchMapping("/api/batch-processes/{batchProcess}")
	@RequiresPermission("batch_process.view")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input, CurrentUser user) {
		Object status = input.get("status");
		String required = !(status instanceof String s) ? null : switch (s) {
			case "completed" -> BatchProcessFlow.COMPLETE_PERMISSION;
			case "skipped" -> BatchProcessFlow.SKIP_PERMISSION;
			case "waiting" -> List.of("waiting", "in_progress").contains(Php.strval(process.get("status"))) ? null
				: BatchProcessFlow.REOPEN_PERMISSION;
			default -> null;
		};
		if (required != null && !permissions.can(user, required)) {
			throw HttpStatusException.forbidden(FORBIDDEN);
		}

		Rules rules = Rules.of()
			.field("status", "sometimes|string|in:waiting,in_progress,completed,failed,skipped")
			.field("operator_id", "nullable|integer|exists:users,id")
			.field("started_at", "nullable|date")
			.field("completed_at", "nullable|date")
			.field("note", "nullable|string");
		Map<String, String> operationRules = operationDataRules(process, input);
		rules.field("operation_data", operationRules.isEmpty() ? "nullable|array" : "required|array");
		operationRules.forEach((field, fieldRules) -> rules.field("operation_data." + field, fieldRules));

		flow.update(process, validator.validate(input, rules), user);
		return detail(process.id());
	}

	/**
	 * Yalnizca asama tamamlanirken (zaten tamamlanmamis/onay beklemiyorsa): her etkin uzantinin alanlari,
	 * sort_order sirasiyla; ayni alan iki uzantida varsa ilki gecerli (PHP {@code +=}).
	 */
	private Map<String, String> operationDataRules(Row process, RequestInput input) {
		Map<String, String> rules = new LinkedHashMap<>();
		if (!"completed".equals(input.get("status")) || List.of("completed", BatchProcessFlow.AWAITING_APPROVAL)
			.contains(Php.strval(process.get("status")))) {
			return rules;
		}
		List<StageHandlers.Extension> extensions = handlers.extensions(Php.intval(process.get("production_stage_id")));
		for (String key : StageHandlers.keys(extensions)) {
			Map<String, String> extensionRules = switch (key) {
				case "weighing" -> weighingRules();
				case "material_transformation" -> transformationRules();
				case "analysis" -> analysisRules();
				case "storage_movement" -> storageMovementRules(extensions, input);
				default -> Map.of();
			};
			extensionRules.forEach(rules::putIfAbsent);
		}
		return rules;
	}

	private static Map<String, String> weighingRules() {
		Map<String, String> r = new LinkedHashMap<>();
		r.put("quantity", "required|numeric|gt:0");
		r.put("unit", "nullable|string|in:KG,LITRE");
		r.put("production_product_type_id", "nullable|integer|exists:production_product_types,id");
		return r;
	}

	private static Map<String, String> transformationRules() {
		String output = "operation_data.outputs.*.";
		Map<String, String> r = new LinkedHashMap<>();
		r.put("input_quantity", "required|numeric|gt:0");
		r.put("input_material_id", "nullable|integer|exists:materials,id");
		r.put("input_production_product_type_id", "nullable|integer|exists:production_product_types,id");
		r.put("outputs", "required|array|min:1");
		r.put("outputs.*", "array");
		r.put("outputs.*.quantity", "required|numeric|gt:0");
		r.put("outputs.*.unit", "nullable|string|in:KG,LITRE");
		r.put("outputs.*.production_product_type_id", "required_without_all:" + output + "production_product_id," + output
			+ "product_type_key|nullable|integer|exists:production_product_types,id");
		r.put("outputs.*.production_product_id", "nullable|integer|exists:production_products,id");
		r.put("outputs.*.product_type_key", "nullable|string");
		return r;
	}

	private static Map<String, String> analysisRules() {
		Map<String, String> r = new LinkedHashMap<>();
		r.put("material_id", "nullable|integer|exists:materials,id");
		r.put("attributes", "required|array|min:1");
		r.put("attributes.*", "array");
		r.put("attributes.*.attribute_key",
			"required_without:operation_data.attributes.*.production_product_attribute_definition_id|nullable|string");
		r.put("attributes.*.production_product_attribute_definition_id",
			"nullable|integer|exists:production_product_attribute_definitions,id");
		r.put("attributes.*.value", "nullable");
		r.put("attributes.*.value_boolean", "nullable|boolean");
		r.put("attributes.*.value_integer", "nullable|integer");
		r.put("attributes.*.value_decimal", "nullable|numeric");
		r.put("attributes.*.value_text", "nullable|string");
		r.put("attributes.*.value_json", "nullable|array");
		return r;
	}

	/** Hareket turu: gonderilen, yoksa asamada sabitlenen, o da yoksa inbound. Transferde tanklar from/to. */
	private static Map<String, String> storageMovementRules(List<StageHandlers.Extension> extensions, RequestInput input) {
		Object action = input.get("operation_data.action");
		if (action == null) {
			action = StageHandlers.pinnedAction(extensions);
		}
		boolean transfer = "transfer".equals(action == null ? "inbound" : action);
		Map<String, String> r = new LinkedHashMap<>();
		r.put("action", "nullable|string|in:inbound,outbound,transfer");
		r.put("material_id", "nullable|integer|exists:materials,id");
		r.put("quantity", "required|numeric|gt:0");
		r.put("note", "nullable|string");
		r.put("storage_unit_id", (transfer ? "nullable" : "required") + "|integer|exists:storage_units,id");
		r.put("from_storage_unit_id", (transfer ? "required" : "nullable") + "|integer|exists:storage_units,id");
		r.put("to_storage_unit_id", (transfer ? "required" : "nullable")
			+ "|integer|exists:storage_units,id|different:operation_data.from_storage_unit_id");
		return r;
	}

	// ---- onay --------------------------------------------------------------

	/** Etki (materyal, tank hareketi) simdi islenir; gonderimden beri kural bozulduysa 422, asama bekler. */
	@PostMapping("/api/batch-processes/{batchProcess}/approve")
	@RequiresPermission("batch_process.approve")
	@Transactional
	DataResponse<Map<String, Object>> approve(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of().field("note", "nullable|string|max:2000"));
		flow.approve(process, user, v.get("note"));
		return detail(process.id());
	}

	/** Gerekce zorunlu; asama gonderilen veriyle isciye (in_progress) doner. */
	@PostMapping("/api/batch-processes/{batchProcess}/reject")
	@RequiresPermission("batch_process.approve")
	@Transactional
	DataResponse<Map<String, Object>> reject(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of().field("note", "required|string|max:2000"));
		flow.reject(process, user, v.get("note"));
		return detail(process.id());
	}

	// ---- yardimcilar -------------------------------------------------------

	private DataResponse<Map<String, Object>> detail(long id) {
		Row process = db.find(TABLE, id).orElseThrow();
		return DataResponse.of(reads.process(process, flow.requiresApproval(process)));
	}

	/** {@code filter_var(FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE)} (deger dogrulanmis: 0/1/true/false). */
	private static Boolean filterBoolean(Object value) {
		return switch (value) {
			case Boolean b -> b;
			case Long l -> l == 1 ? Boolean.TRUE : l == 0 ? Boolean.FALSE : null;
			case String s -> s.equals("1") ? Boolean.TRUE : s.equals("0") ? Boolean.FALSE : null;
			default -> null;
		};
	}

	private static List<?> list(Object value) {
		return value instanceof List<?> l ? l : value instanceof Map<?, ?> m ? new ArrayList<>(m.values()) : List.of();
	}

}
