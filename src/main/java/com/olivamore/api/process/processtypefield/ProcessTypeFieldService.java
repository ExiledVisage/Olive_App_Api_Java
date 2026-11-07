package com.olivamore.api.process.processtypefield;

import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProcessTypeFieldService}. */
@Service
class ProcessTypeFieldService {

	static final String FIELD_NOT_IN_TYPE = "Field not found for this process type.";

	private final Db db;

	ProcessTypeFieldService(Db db) {
		this.db = db;
	}

	List<Row> forProcessType(Row processType) {
		return db.select("""
			select * from process_type_fields
			where process_type_fields.process_type_id = ? and process_type_fields.process_type_id is not null
			order by sort_order asc, sort_order asc, id asc""", processType.id());
	}

	static void ensureBelongs(Row processType, Row field) {
		if (field.longValue("process_type_id") != processType.id()) {
			throw new HttpStatusException(404, FIELD_NOT_IN_TYPE);
		}
	}

	Row create(Row processType, Map<String, Object> validated) {
		Map<String, Object> values = jsonOptions(validated);
		values.put("process_type_id", processType.id());
		return db.create("process_type_fields", values);
	}

	Row update(Row field, Map<String, Object> validated) {
		db.update("process_type_fields", field.id(), jsonOptions(validated));
		return db.find("process_type_fields", field.id()).orElseThrow();
	}

	void delete(Row field) {
		DeletionGuard.of("'" + field.string("label") + "' alanı")
			.usedBy("girilmiş ölçüm", db.count("select count(*) from batch_process_field_values where process_type_field_id = ?", field.id()))
			.check("Artık kullanılmayacaksa is_active alanını false yapın.");
		db.delete("process_type_fields", field.id());
	}

	/** {@code 'options' => 'array'} cast'i: JSON olarak yazilir. */
	private static Map<String, Object> jsonOptions(Map<String, Object> validated) {
		Map<String, Object> values = new LinkedHashMap<>(validated);
		if (values.containsKey("options")) {
			values.put("options", Db.json(values.get("options")));
		}
		return values;
	}

}
