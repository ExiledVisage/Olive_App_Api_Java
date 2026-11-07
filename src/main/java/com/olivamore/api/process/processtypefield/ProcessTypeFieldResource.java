package com.olivamore.api.process.processtypefield;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProcessTypeFieldResource}. */
@Component
public class ProcessTypeFieldResource {

	private final Db db;

	ProcessTypeFieldResource(Db db) {
		this.db = db;
	}

	public List<Map<String, Object>> fields(List<Row> fields) {
		return fields.stream().map(ProcessTypeFieldResource::of).toList();
	}

	/** Proses tipine gore (sort_order sirasinda); {@code fields} iliskisinin eager loading'i. */
	public Map<Long, List<Map<String, Object>>> byProcessType(List<Long> processTypeIds) {
		return Relations.groupItems(fields(db.whereIn("process_type_fields", "process_type_id", processTypeIds,
			"sort_order asc")), "process_type_id");
	}

	public static Map<String, Object> of(Row f) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", f.get("id"));
		r.put("process_type_id", f.get("process_type_id"));
		r.put("field_key", f.get("field_key"));
		r.put("label", f.get("label"));
		r.put("data_type", f.get("data_type"));
		r.put("is_required", Casts.bool(f.get("is_required")));
		r.put("unit", f.get("unit"));
		r.put("sort_order", f.get("sort_order"));
		r.put("is_active", Casts.bool(f.get("is_active")));
		r.put("options", f.get("options"));
		r.put("default_value", f.get("default_value"));
		r.put("created_at", f.get("created_at"));
		r.put("updated_at", f.get("updated_at"));
		return r;
	}

}
