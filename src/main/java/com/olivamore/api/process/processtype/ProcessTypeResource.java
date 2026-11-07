package com.olivamore.api.process.processtype;

import com.olivamore.api.process.processtypefield.ProcessTypeFieldResource;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProcessTypeResource} (alanlari yuklu). */
@Component
public class ProcessTypeResource {

	private final ProcessTypeFieldResource fieldResource;

	ProcessTypeResource(ProcessTypeFieldResource fieldResource) {
		this.fieldResource = fieldResource;
	}

	public List<Map<String, Object>> processTypes(List<Row> types) {
		Map<Long, List<Map<String, Object>>> fields = fieldResource.byProcessType(Relations.ids(types));
		return types.stream().map(t -> of(t, fields.getOrDefault(t.id(), List.of()))).toList();
	}

	/** {@code fields} null ise yuklenmemis (anahtar yazilmaz). */
	static Map<String, Object> of(Row type, List<Map<String, Object>> fields) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", type.get("id"));
		r.put("name", type.get("name"));
		r.put("description", type.get("description"));
		r.put("category", type.get("category"));
		r.put("is_active", Casts.bool(type.get("is_active")));
		if (fields != null) {
			r.put("fields", fields);
		}
		r.put("created_at", type.get("created_at"));
		r.put("updated_at", type.get("updated_at"));
		return r;
	}

}
