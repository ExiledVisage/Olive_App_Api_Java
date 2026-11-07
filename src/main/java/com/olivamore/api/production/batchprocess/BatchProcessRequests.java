package com.olivamore.api.production.batchprocess;

import com.olivamore.api.production.batchprocess.stage.StageExtensions;
import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Permissions;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.web.RequestInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code Http/Requests/BatchProcess}: UpdateBatchProcessRequest (yetki ve asamanin uzantilarina gore
 * {@code operation_data}), SyncFieldValuesRequest (alanin veri tipi), onay/red. Kurallar veritabanina baktigi icin
 * (alan tipi, asamanin uzantilari) bilesen.
 */
@Component
class BatchProcessRequests {

	private static final String FORBIDDEN = "This action is unauthorized.";

	private final Db db;
	private final StageExtensions handlers;
	private final Permissions permissions;

	BatchProcessRequests(Db db, StageExtensions handlers, Permissions permissions) {
		this.db = db;
		this.handlers = handlers;
		this.permissions = permissions;
	}

	// ---- alan degerleri ----------------------------------------------------

	static Rules syncFieldValues() {
		return Rules.of()
			.field("fields", "required|array|min:1")
			.field("fields.*.process_type_field_id", "required|integer|exists:process_type_fields,id")
			.field("fields.*.value", "nullable");
	}

	/** {@code withValidator}: deger alanin veri tipine uymali (null serbest). */
	LaravelValidator.After fieldValueTypes() {
		return this::checkFieldValues;
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

	/** {@code authorize()}: tamamlama, atlama ve kapanmis asamayi geri acma ayri yetkiler; dogrulamadan once (403). */
	void authorizeUpdate(Row process, RequestInput input, CurrentUser user) {
		Object status = input.get("status");
		String required = !(status instanceof String s) ? null : switch (s) {
			case "completed" -> BatchProcessService.COMPLETE_PERMISSION;
			case "skipped" -> BatchProcessService.SKIP_PERMISSION;
			case "waiting" -> List.of("waiting", "in_progress").contains(Php.strval(process.get("status"))) ? null
				: BatchProcessService.REOPEN_PERMISSION;
			default -> null;
		};
		if (required != null && !permissions.can(user, required)) {
			throw HttpStatusException.forbidden(FORBIDDEN);
		}
	}

	/** Tamamlanirken {@code operation_data}'nin sekli asamanin uzantilarina gore. */
	Rules update(Row process, RequestInput input) {
		Rules rules = Rules.of()
			.field("status", "sometimes|string|in:waiting,in_progress,completed,failed,skipped")
			.field("operator_id", "nullable|integer|exists:users,id")
			.field("started_at", "nullable|date")
			.field("completed_at", "nullable|date")
			.field("note", "nullable|string");
		Map<String, String> operationRules = operationDataRules(process, input);
		rules.field("operation_data", operationRules.isEmpty() ? "nullable|array" : "required|array");
		operationRules.forEach((field, fieldRules) -> rules.field("operation_data." + field, fieldRules));
		return rules;
	}

	/**
	 * Yalnizca asama tamamlanirken (zaten tamamlanmamis/onay beklemiyorsa): her etkin uzantinin alanlari,
	 * sort_order sirasiyla; ayni alan iki uzantida varsa ilki gecerli (PHP {@code +=}).
	 */
	private Map<String, String> operationDataRules(Row process, RequestInput input) {
		Map<String, String> rules = new LinkedHashMap<>();
		if (!"completed".equals(input.get("status")) || List.of("completed", BatchProcessService.AWAITING_APPROVAL)
			.contains(Php.strval(process.get("status")))) {
			return rules;
		}
		List<StageExtensions.Extension> extensions = handlers.extensions(Php.intval(process.get("production_stage_id")));
		for (String key : StageExtensions.keys(extensions)) {
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
	private static Map<String, String> storageMovementRules(List<StageExtensions.Extension> extensions, RequestInput input) {
		Object action = input.get("operation_data.action");
		if (action == null) {
			action = StageExtensions.pinnedAction(extensions);
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

	static Rules approve() {
		return Rules.of().field("note", "nullable|string|max:2000");
	}

	static Rules reject() {
		return Rules.of().field("note", "required|string|max:2000");
	}

}
