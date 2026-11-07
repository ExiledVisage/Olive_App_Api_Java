package com.olivamore.api.process.processtype;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Laravel {@code ProcessTypeService}. */
@Service
class ProcessTypeService {

	private final Db db;

	ProcessTypeService(Db db) {
		this.db = db;
	}

	List<Row> all() {
		return db.select("select * from process_types order by created_at desc");
	}

	Row create(Map<String, Object> validated) {
		return db.create("process_types", validated);
	}

	Row update(Row processType, Map<String, Object> validated) {
		db.update("process_types", processType.id(), validated);
		return db.find("process_types", processType.id()).orElseThrow();
	}

	void delete(Row processType) {
		DeletionGuard.of("'" + processType.string("name") + "' proses tipi")
			.usedBy("üretim aşaması", db.count("select count(*) from production_stages where process_type_id = ?", processType.id()))
			.usedBy("girilmiş ölçüm", db.count("select count(*) from batch_process_field_values where process_type_field_id in"
				+ " (select id from process_type_fields where process_type_id = ?)", processType.id()))
			.check();
		db.delete("process_types", processType.id());
	}

}
