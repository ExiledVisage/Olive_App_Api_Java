package com.olivamore.api.product.productionproduct;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionProductService}. */
@Service
class ProductionProductService {

	private final Db db;

	ProductionProductService(Db db) {
		this.db = db;
	}

	/** {@code withCount('productionProductTypes')}, en yeni once. */
	List<Row> all() {
		return db.select("""
			select production_products.*, (select count(*) from production_product_types
			  where production_products.id = production_product_types.production_product_id) as production_product_types_count
			from production_products order by created_at desc""");
	}

	List<Row> types(long productId) {
		return db.whereIn("production_product_types", "production_product_id", List.of(productId), null);
	}

	long typeCount(long productId) {
		return db.count("select count(*) from production_product_types where production_product_id = ?", productId);
	}

	Row create(Map<String, Object> validated) {
		return db.create("production_products", validated);
	}

	Row update(Row product, Map<String, Object> validated) {
		db.update("production_products", product.id(), validated);
		return db.find("production_products", product.id()).orElseThrow();
	}

	void delete(Row product) {
		long id = product.id();
		String mappings = "(select id from production_product_attribute_definitions where production_product_id = " + id + ")";
		DeletionGuard.of("'" + product.string("name") + "' ürün kategorisi")
			.usedBy("ürün tipi", countBy("production_product_types", id))
			.usedBy("tank bakiyesi", countBy("storage_unit_balances", id))
			.usedBy("tank izni", countBy("storage_unit_allowed_production_products", id))
			.usedBy("hat girdisi", countBy("production_line_input_production_products", id))
			.usedBy("hat çıktısı", countBy("production_line_output_production_products", id))
			.usedBy("aşama extension girdisi", countBy("production_stage_extension_input_production_products", id))
			.usedBy("aşama extension çıktısı", countBy("production_stage_extension_output_production_products", id))
			.usedBy("materyal attribute değeri", db.count("select count(*) from material_attribute_values"
				+ " where production_product_attribute_definition_id in " + mappings))
			.usedBy("tank attribute kuralı", db.count("select count(*) from storage_unit_attribute_rules"
				+ " where production_product_attribute_definition_id in " + mappings))
			.check();
		db.delete("production_products", id);
	}

	private long countBy(String table, long id) {
		return db.count("select count(*) from " + table + " where production_product_id = ?", id);
	}

}
