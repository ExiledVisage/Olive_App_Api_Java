package com.olivamore.api.catalog;

import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ProductionStageService'in sira mantigi, birebir. {@code (production_line_id, stage_order)} unique
 * oldugu icin kaydirma iki adimda: once 100000 ileri, sonra geri (+1 ya da -1 net).
 */
@Component
class StageOrder {

	private static final int TEMP_OFFSET = 100000;

	private final Db db;

	StageOrder(Db db) {
		this.db = db;
	}

	/** Istenen sira 1..(son+1) araligina cekilir, o siradan sonrakiler bir ileri kayar. */
	Row create(long lineId, Map<String, Object> validated) {
		int desired = (int) Php.intval(validated.getOrDefault("stage_order", 1));
		int maxInsert = max(lineId) + 1;
		desired = Math.max(1, Math.min(desired, maxInsert));
		shiftUp(lineId, desired, maxInsert - 1);
		Map<String, Object> values = new LinkedHashMap<>(validated);
		values.put("production_line_id", lineId);
		values.put("stage_order", desired);
		return db.create("production_stages", values);
	}

	void update(long lineId, Row stage, Map<String, Object> validated) {
		Map<String, Object> values = new LinkedHashMap<>(validated);
		int oldOrder = stage.intValue("stage_order");
		if (values.containsKey("stage_order") && values.get("stage_order") != null) {
			int desired = (int) Php.intval(values.get("stage_order"));
			desired = Math.max(1, Math.min(desired, max(lineId)));
			if (desired != oldOrder) {
				int temp = max(lineId) + TEMP_OFFSET + (int) stage.id();
				db.update("production_stages", stage.id(), Map.of("stage_order", temp));
				if (desired < oldOrder) {
					shiftUp(lineId, desired, oldOrder - 1);
				}
				else {
					shiftDown(lineId, oldOrder + 1, desired);
				}
				values.put("stage_order", desired);
			}
			else {
				values.remove("stage_order");
			}
		}
		db.update("production_stages", stage.id(), values);
	}

	void delete(long lineId, Row stage) {
		int deleted = stage.intValue("stage_order");
		int maxOrder = max(lineId);
		db.delete("production_stages", stage.id());
		shiftDown(lineId, deleted + 1, maxOrder);
	}

	private int max(long lineId) {
		return (int) db.count("select coalesce(max(stage_order), 0) from production_stages where production_line_id = ?", lineId);
	}

	/** increment/decrement: Eloquent updated_at'i da gunceller. */
	private void shiftUp(long lineId, int start, int end) {
		shift(lineId, start, end, TEMP_OFFSET - 1);
	}

	private void shiftDown(long lineId, int start, int end) {
		shift(lineId, start, end, TEMP_OFFSET + 1);
	}

	private void shift(long lineId, int start, int end, int back) {
		if (start > end) {
			return;
		}
		String now = "'" + com.olivamore.api.shared.time.Timestamps.now().toString().replace('T', ' ') + "'";
		db.execute("update production_stages set stage_order = stage_order + " + TEMP_OFFSET + ", updated_at = " + now
			+ " where production_line_id = ? and stage_order between ? and ?", lineId, start, end);
		db.execute("update production_stages set stage_order = stage_order - " + back + ", updated_at = " + now
			+ " where production_line_id = ? and stage_order between ? and ?", lineId, start + TEMP_OFFSET, end + TEMP_OFFSET);
	}

}
