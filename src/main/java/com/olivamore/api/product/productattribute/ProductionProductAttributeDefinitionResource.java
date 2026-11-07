package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionProductAttributeDefinitionResource}: kategori-attribute eslemesi. */
@Component
public class ProductionProductAttributeDefinitionResource {

	private final Db db;

	ProductionProductAttributeDefinitionResource(Db db) {
		this.db = db;
	}

	/** Kategorinin eslemeleri, eager loading sirasiyla (siralamasiz); attributeDefinition yuklu degil. */
	public List<Map<String, Object>> ofProduct(long productId) {
		return mappings(db.whereIn("production_product_attribute_definitions", "production_product_id", List.of(productId), null), false);
	}

	/**
	 * {@code withDefinition}: attributeDefinition eager yuklu mu ("definition" anahtari); degilse alanlar
	 * yine accessor ile (lazy) gelir.
	 */
	public List<Map<String, Object>> mappings(List<Row> mappings, boolean withDefinition) {
		Map<Long, Row> definitions = Relations.byId(db.whereIn("attribute_definitions", "id",
			Relations.column(mappings, "attribute_definition_id"), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row m : mappings) {
			Row d = definitions.get(m.longValue("attribute_definition_id"));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", m.get("id"));
			r.put("production_product_id", m.get("production_product_id"));
			r.put("is_required", Boolean.TRUE.equals(m.get("is_required")));
			r.put("sort_order", m.intValue("sort_order") == null ? 0 : m.intValue("sort_order"));
			if (withDefinition) {
				Map<String, Object> def = new LinkedHashMap<>();
				def.put("id", d == null ? null : d.get("id"));
				def.put("attribute_key", d == null ? null : d.get("attribute_key"));
				def.put("label", d == null ? null : d.get("label"));
				def.put("data_type", d == null ? null : d.get("data_type"));
				def.put("unit", d == null ? null : d.get("unit"));
				def.put("description", d == null ? null : d.get("description"));
				r.put("definition", def);
			}
			r.put("attribute_key", d == null ? null : d.get("attribute_key"));
			r.put("label", d == null ? null : d.get("label"));
			r.put("data_type", d == null ? null : d.get("data_type"));
			r.put("unit", d == null ? null : d.get("unit"));
			r.put("created_at", m.get("created_at"));
			r.put("updated_at", m.get("updated_at"));
			out.add(r);
		}
		return out;
	}

}
