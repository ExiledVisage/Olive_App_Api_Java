package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionStageExtensionService::sync}, birebir: asamanin uzantilari silinip yeniden olusturulur. */
@Service
class ProductionStageExtensionService {

	static final String INVALID_FIELD = "Geçersiz stage extension field gönderildi.";

	private final Db db;

	ProductionStageExtensionService(Db db) {
		this.db = db;
	}

	void sync(long stageId, List<Map<String, Object>> extensions) {
		db.execute("delete from production_stage_extensions where production_stage_id = ?", stageId);
		for (int i = 0; i < extensions.size(); i++) {
			Map<String, Object> data = extensions.get(i);
			long typeId = Php.intval(data.get("stage_extension_type_id"));
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("stage_extension_type_id", data.get("stage_extension_type_id"));
			values.put("is_required", data.get("is_required") != null ? data.get("is_required") : false);
			values.put("is_enabled", data.get("is_enabled") != null ? data.get("is_enabled") : true);
			values.put("sort_order", data.get("sort_order") != null ? data.get("sort_order") : i + 1);
			values.put("production_stage_id", stageId);
			long extensionId = db.insert("production_stage_extensions", values);
			storeValues(extensionId, typeId, items(data.get("values")));
			storeProducts("production_stage_extension_input_production_products", extensionId, list(data.get("input_production_product_ids")));
			storeProducts("production_stage_extension_output_production_products", extensionId, list(data.get("output_production_product_ids")));
		}
	}

	private void storeValues(long extensionId, long typeId, List<Map<String, Object>> values) {
		List<Row> fields = db.select("select * from stage_extension_type_fields where stage_extension_type_id = ?", typeId);
		for (Map<String, Object> value : values) {
			Row field = null;
			Object fieldId = value.get("stage_extension_type_field_id");
			Object fieldKey = value.get("field_key");
			if (!empty(fieldId)) {
				long id = Php.intval(fieldId);
				field = fields.stream().filter(f -> f.id() == id).findFirst().orElse(null);
			}
			else if (!empty(fieldKey)) {
				field = fields.stream().filter(f -> Php.strval(fieldKey).equals(f.string("field_key"))).findFirst().orElse(null);
			}
			if (field == null) {
				throw LaravelValidationException.of("extensions.values", INVALID_FIELD);
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("stage_extension_type_field_id", field.id());
			payload.put("value_boolean", value.get("value_boolean"));
			payload.put("value_integer", value.get("value_integer"));
			payload.put("value_decimal", value.get("value_decimal"));
			payload.put("value_text", value.get("value_text"));
			payload.put("value_json", Db.json(value.containsKey("value_json") ? value.get("value_json") : null));
			boolean empty = payload.get("value_boolean") == null && payload.get("value_integer") == null
				&& payload.get("value_decimal") == null && payload.get("value_text") == null
				&& ((Db.Json) payload.get("value_json")).value() == null;
			if (empty) {
				continue;
			}
			payload.put("production_stage_extension_id", extensionId);
			db.insert("production_stage_extension_values", payload);
		}
	}

	private void storeProducts(String table, long extensionId, List<Object> productIds) {
		for (int i = 0; i < productIds.size(); i++) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("production_product_id", productIds.get(i));
			values.put("sort_order", i + 1);
			values.put("is_required", true);
			values.put("production_stage_extension_id", extensionId);
			db.insert(table, values);
		}
	}

	/** PHP empty(): null, "", "0", 0, false, [] */
	private static boolean empty(Object v) {
		return v == null || "".equals(v) || "0".equals(v) || Boolean.FALSE.equals(v)
			|| (v instanceof Number n && Php.intval(n) == 0 && n.doubleValue() == 0) || (v instanceof List<?> l && l.isEmpty());
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(Object v) {
		if (v instanceof List<?> l) {
			return (List<Object>) l;
		}
		if (v instanceof Map<?, ?> m) {
			return List.copyOf(((Map<String, Object>) m).values());
		}
		return List.of();
	}

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> items(Object value) {
		if (value instanceof List<?> list) {
			return list.stream().map(i -> i instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
		}
		if (value instanceof Map<?, ?> map) {
			return ((Map<String, Object>) map).values().stream()
				.map(i -> i instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
		}
		return List.of();
	}

}
