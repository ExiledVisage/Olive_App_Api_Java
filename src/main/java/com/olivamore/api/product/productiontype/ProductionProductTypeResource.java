package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionProductTypeResource} (productionProduct her baglamda yuklu). */
@Component
public class ProductionProductTypeResource {

	private final Db db;

	ProductionProductTypeResource(Db db) {
		this.db = db;
	}

	public List<Map<String, Object>> productTypes(List<Row> types) {
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			types.stream().map(t -> Relations.key(t.get("production_product_id"))).toList(), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row t : types) {
			Row product = products.get(Relations.key(t.get("production_product_id")));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", t.get("id"));
			r.put("name", t.get("name"));
			r.put("code", t.get("code"));
			r.put("type", t.get("type"));
			r.put("unit", t.get("unit"));
			r.put("density_kg_per_litre", Casts.decimal(t.get("density_kg_per_litre"), 4));
			r.put("effective_density_kg_per_litre", effectiveDensity(t, product));
			r.put("default_density_kg_per_litre", product == null ? null : Casts.decimal(product.get("default_density_kg_per_litre"), 4));
			r.put("production_product_id", t.get("production_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product == null ? null : product.get("id"));
			p.put("key", product == null ? null : product.get("key"));
			p.put("name", product == null ? null : product.get("name"));
			p.put("description", product == null ? null : product.get("description"));
			r.put("production_product", p);
			r.put("created_at", t.get("created_at"));
			r.put("updated_at", t.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	public Map<String, Object> productType(Row type) {
		return productTypes(List.of(type)).getFirst();
	}

	/** ProductionProductType::effectiveDensity(): kendi > 0 ise o, yoksa kategorinin varsayilani > 0 ise o. */
	private static Double effectiveDensity(Row type, Row product) {
		BigDecimal own = Casts.toDecimal(type.get("density_kg_per_litre"));
		if (own != null && own.signum() > 0) {
			return own.doubleValue();
		}
		BigDecimal fallback = product == null ? null : Casts.toDecimal(product.get("default_density_kg_per_litre"));
		return fallback != null && fallback.signum() > 0 ? fallback.doubleValue() : null;
	}

}
