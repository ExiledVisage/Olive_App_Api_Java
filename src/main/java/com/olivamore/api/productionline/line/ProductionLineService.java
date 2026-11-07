package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.ValidatedInput;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionLineService}. */
@Service
class ProductionLineService {

	private static final String INPUTS = "production_line_input_production_products";
	private static final String OUTPUTS = "production_line_output_production_products";

	private final Db db;

	ProductionLineService(Db db) {
		this.db = db;
	}

	List<Row> all() {
		return db.select("select * from production_lines order by created_at desc");
	}

	Row create(ValidatedInput v) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("description", v.get("description"));
		long id = db.insert("production_lines", values);
		replaceProducts(INPUTS, id, items(v.get("input_production_products")));
		replaceProducts(OUTPUTS, id, items(v.get("output_production_products")));
		return db.find("production_lines", id).orElseThrow();
	}

	Row update(Row line, ValidatedInput v) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name") != null ? v.get("name") : line.get("name"));
		values.put("description", v.has("description") ? v.get("description") : line.get("description"));
		db.update("production_lines", line.id(), values);
		if (v.has("input_production_products")) {
			replaceProducts(INPUTS, line.id(), items(v.get("input_production_products")));
		}
		if (v.has("output_production_products")) {
			replaceProducts(OUTPUTS, line.id(), items(v.get("output_production_products")));
		}
		return db.find("production_lines", line.id()).orElseThrow();
	}

	void delete(Row line) {
		DeletionGuard.of("'" + line.string("name") + "' hattı")
			.usedBy("üretim partisi", db.count("select count(*) from production_batches where production_line_id = ?", line.id()))
			.check();
		db.execute("delete from " + INPUTS + " where production_line_id = ?", line.id());
		db.execute("delete from " + OUTPUTS + " where production_line_id = ?", line.id());
		db.delete("production_lines", line.id());
	}

	/** replaceInputProductTypes / replaceOutputProductTypes */
	private void replaceProducts(String table, long lineId, List<Map<String, Object>> items) {
		db.execute("delete from " + table + " where production_line_id = ?", lineId);
		for (int i = 0; i < items.size(); i++) {
			Map<String, Object> item = items.get(i);
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("production_product_id", item.get("production_product_id"));
			values.put("production_product_type_id", item.get("production_product_type_id"));
			values.put("sort_order", item.get("sort_order") != null ? item.get("sort_order") : i + 1);
			values.put("production_line_id", lineId);
			db.insert(table, values);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
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
