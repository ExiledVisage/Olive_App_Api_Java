package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionProductTypeService}. */
@Service
class ProductionProductTypeService {

	private final Db db;

	ProductionProductTypeService(Db db) {
		this.db = db;
	}

	List<Row> all() {
		return db.select("select * from production_product_types order by created_at desc");
	}

	Row create(Map<String, Object> validated) {
		return db.create("production_product_types", validated);
	}

	Row update(Row type, Map<String, Object> validated) {
		db.update("production_product_types", type.id(), validated);
		return db.find("production_product_types", type.id()).orElseThrow();
	}

	void delete(Row type) {
		long id = type.id();
		DeletionGuard.of("'" + type.string("name") + "' ürün tipi")
			.usedBy("materyal", countBy("materials", id))
			.usedBy("parti girdisi", countBy("production_batch_input_production_product_types", id))
			.usedBy("parti çıktısı", countBy("production_batch_output_production_product_types", id))
			.usedBy("tank izni", countBy("storage_unit_allowed_production_product_types", id))
			.usedBy("tarif içeriği", countBy("finished_product_recipe_contents", id))
			.check();
		db.delete("production_product_types", id);
	}

	private long countBy(String table, long id) {
		return db.count("select count(*) from " + table + " where production_product_type_id = ?", id);
	}

}
