package com.olivamore.api.process.stageextensiontype;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code StageExtensionTypeResource} ve {@code StageExtensionTypeFieldResource}. */
@Component
public class StageExtensionTypeResource {

	private final Db db;

	StageExtensionTypeResource(Db db) {
		this.db = db;
	}

	List<Map<String, Object>> types(List<Row> types) {
		Map<Long, List<Map<String, Object>>> fields = fieldsByType(Relations.ids(types), "sort_order asc, sort_order asc");
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row t : types) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", t.get("id"));
			r.put("key", t.get("key"));
			r.put("name", t.get("name"));
			r.put("description", t.get("description"));
			r.put("fields", fields.getOrDefault(t.id(), List.of()));
			r.put("created_at", t.get("created_at"));
			r.put("updated_at", t.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** Uzanti tipine gore alanlar ({@code fields} iliskisinin eager loading'i). */
	public Map<Long, List<Map<String, Object>>> fieldsByType(List<Long> typeIds, String orderBy) {
		return Relations.groupItems(db.whereIn("stage_extension_type_fields", "stage_extension_type_id", typeIds, orderBy)
			.stream().map(StageExtensionTypeResource::field).toList(), "stage_extension_type_id");
	}

	/** StageExtensionTypeFieldResource */
	public static Map<String, Object> field(Row f) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", f.get("id"));
		r.put("stage_extension_type_id", f.get("stage_extension_type_id"));
		r.put("field_key", f.get("field_key"));
		r.put("label", f.get("label"));
		r.put("data_type", f.get("data_type"));
		r.put("is_required", Boolean.TRUE.equals(f.get("is_required")));
		r.put("sort_order", f.intValue("sort_order") == null ? 0 : f.intValue("sort_order"));
		r.put("default_value_boolean", f.get("default_value_boolean"));
		r.put("default_value_integer", f.get("default_value_integer"));
		r.put("default_value_decimal", Casts.decimal(f.decimal("default_value_decimal"), 4));
		r.put("default_value_text", f.get("default_value_text"));
		r.put("default_value_json", f.get("default_value_json"));
		r.put("options_json", f.get("options_json"));
		r.put("description", f.get("description"));
		return r;
	}

}
