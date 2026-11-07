package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.ValidatedInput;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionProductAttributeDefinitionService}. */
@Service
class ProductionProductAttributeDefinitionService {

	static final String MAPPING_NOT_IN_PRODUCT = "Seçilen attribute definition bu ürün kategorisine ait değil.";

	private final Db db;

	ProductionProductAttributeDefinitionService(Db db) {
		this.db = db;
	}

	List<Row> forProduct(Row product) {
		return db.select("""
			select * from production_product_attribute_definitions
			where production_product_attribute_definitions.production_product_id = ?
			  and production_product_attribute_definitions.production_product_id is not null
			order by sort_order asc, id asc""", product.id());
	}

	static void ensureBelongs(Row product, Row mapping) {
		if (mapping.longValue("production_product_id") != product.id()) {
			throw new BusinessRuleException(MAPPING_NOT_IN_PRODUCT);
		}
	}

	/**
	 * Laravel'de her zaman 500 (unique kuralinda Query Builder'da olmayan whereHas). Burada amaclanan:
	 * anahtar bu kategoride zaten eslenmisse 422; global tanim yoksa olusur (firstOrCreate).
	 */
	Row create(Row product, ValidatedInput v) {
		assertKeyNotMapped(product.id(), v.string("attribute_key"), null);
		Row definition = db.first("select * from attribute_definitions where attribute_key = ? limit 1", v.get("attribute_key"))
			.orElseGet(() -> {
				Map<String, Object> values = new LinkedHashMap<>();
				values.put("attribute_key", v.get("attribute_key"));
				values.put("label", v.get("label"));
				values.put("data_type", v.get("data_type"));
				values.put("unit", v.get("unit"));
				values.put("description", v.get("description"));
				return db.find("attribute_definitions", db.insert("attribute_definitions", values)).orElseThrow();
			});
		Map<String, Object> mapping = new LinkedHashMap<>();
		mapping.put("attribute_definition_id", definition.id());
		mapping.put("is_required", v.get("is_required") == null ? false : v.get("is_required"));
		mapping.put("sort_order", v.get("sort_order") == null ? 0 : v.get("sort_order"));
		mapping.put("production_product_id", product.id());
		long id = db.insert("production_product_attribute_definitions", mapping);
		return db.find("production_product_attribute_definitions", id).orElseThrow();
	}

	/**
	 * Laravel'de {@code attribute_key} gonderilince 500 (unique kurali eslemeler tablosunda olmayan kolona
	 * bakiyor); servis anahtari zaten degistirmiyor. Burada: bu kategoride baska eslemede varsa 422, yoksa
	 * yok sayilir. Bos (null) degerler degismez ({@code array_filter}).
	 */
	Row update(Row product, Row mapping, ValidatedInput v) {
		if (v.has("attribute_key")) {
			assertKeyNotMapped(product.id(), v.string("attribute_key"), mapping.id());
		}
		ensureBelongs(product, mapping);
		Map<String, Object> definition = new LinkedHashMap<>();
		putIfNotNull(definition, "label", v.get("label"));
		putIfNotNull(definition, "unit", v.get("unit"));
		putIfNotNull(definition, "description", v.get("description"));
		db.update("attribute_definitions", mapping.longValue("attribute_definition_id"), definition);
		Map<String, Object> values = new LinkedHashMap<>();
		putIfNotNull(values, "is_required", v.get("is_required"));
		putIfNotNull(values, "sort_order", v.get("sort_order"));
		db.update("production_product_attribute_definitions", mapping.id(), values);
		return db.find("production_product_attribute_definitions", mapping.id()).orElseThrow();
	}

	void delete(Row product, Row mapping) {
		ensureBelongs(product, mapping);
		String label = db.first("select label from attribute_definitions where id = ?", mapping.get("attribute_definition_id"))
			.map(r -> r.string("label")).orElse(null);
		DeletionGuard.of("'" + label + "' attribute eşlemesi")
			.usedBy("materyal değeri", db.count("select count(*) from material_attribute_values"
				+ " where production_product_attribute_definition_id = ?", mapping.id()))
			.usedBy("tank attribute kuralı", db.count("select count(*) from storage_unit_attribute_rules"
				+ " where production_product_attribute_definition_id = ?", mapping.id()))
			.check();
		db.delete("production_product_attribute_definitions", mapping.id());
	}

	private void assertKeyNotMapped(long productId, String key, Long exceptMapping) {
		long existing = db.count("""
			select count(*) from production_product_attribute_definitions m
			join attribute_definitions d on d.id = m.attribute_definition_id
			where m.production_product_id = ? and d.attribute_key = ? and m.id <> ?""", productId, key, exceptMapping == null ? 0 : exceptMapping);
		if (existing > 0) {
			throw LaravelValidationException.of("attribute_key", "The attribute key has already been taken.");
		}
	}

	private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}

}
