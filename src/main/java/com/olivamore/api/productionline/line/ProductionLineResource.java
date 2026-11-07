package com.olivamore.api.productionline.line;

import com.olivamore.api.productionline.stage.ProductionStageResource;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code ProductionLineResource}: girdi/cikti urunleri ve asamalar.
 * <p>
 * Iliskiler Laravel'in sorgulariyla ayni ORDER BY ile yuklenir; {@code whenLoaded} ile yuklenmemis
 * iliskinin anahtari hic yazilmaz.
 */
@Component
public class ProductionLineResource {

	/** Hangi iliskilerin yuklenecegi (Laravel'deki with([...]) listeleri). */
	public record LineLoad(boolean lineProductTypes, boolean extensions, boolean extensionTypeFields) {

		/** ProductionLineService::getAll / getById */
		public static final LineLoad FULL = new LineLoad(true, true, true);

		/** ProductionProductTypeService::lineRelations, ProductionProductService::getById (tip ve uzanti yok) */
		public static final LineLoad BASIC = new LineLoad(false, false, false);

	}

	private final Db db;
	private final ProductionStageResource stageResource;

	ProductionLineResource(Db db, ProductionStageResource stageResource) {
		this.db = db;
		this.stageResource = stageResource;
	}

	public List<Map<String, Object>> lines(List<Row> lines, LineLoad load) {
		List<Long> ids = Relations.ids(lines);
		Map<Long, List<Map<String, Object>>> inputs = lineProducts("production_line_input_production_products", ids, load);
		Map<Long, List<Map<String, Object>>> outputs = lineProducts("production_line_output_production_products", ids, load);
		Map<Long, List<Map<String, Object>>> stages = stageResource.byLine(ids, load.extensions(), load.extensionTypeFields());

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row line : lines) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", line.get("id"));
			r.put("name", line.get("name"));
			r.put("description", line.get("description"));
			r.put("input_production_products", inputs.getOrDefault(line.id(), List.of()));
			r.put("output_production_products", outputs.getOrDefault(line.id(), List.of()));
			r.put("stages", stages.getOrDefault(line.id(), List.of()));
			r.put("created_at", line.get("created_at"));
			r.put("updated_at", line.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	public Map<String, Object> line(Row line, LineLoad load) {
		return lines(List.of(line), load).getFirst();
	}

	/** ProductionLine(Input|Output)ProductionProductResource */
	private Map<Long, List<Map<String, Object>>> lineProducts(String table, List<Long> lineIds, LineLoad load) {
		List<Row> rows = db.whereIn(table, "production_line_id", lineIds, "sort_order asc");
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id", Relations.column(rows, "production_product_id"), null));
		Map<Long, Row> types = load.lineProductTypes()
			? Relations.byId(db.whereIn("production_product_types", "id", Relations.column(rows, "production_product_type_id"), null))
			: Map.of();
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row row : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", row.get("id"));
			r.put("production_line_id", row.get("production_line_id"));
			r.put("production_product_id", row.get("production_product_id"));
			r.put("production_product_type_id", row.get("production_product_type_id"));
			r.put("sort_order", row.get("sort_order"));
			Row product = products.get(row.longValue("production_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product == null ? null : product.get("id"));
			p.put("key", product == null ? null : product.get("key"));
			p.put("name", product == null ? null : product.get("name"));
			r.put("production_product", p);
			if (load.lineProductTypes()) {
				Row type = row.longValue("production_product_type_id") == null ? null : types.get(row.longValue("production_product_type_id"));
				if (type == null) {
					r.put("production_product_type", null);
				}
				else {
					Map<String, Object> t = new LinkedHashMap<>();
					t.put("id", type.get("id"));
					t.put("name", type.get("name"));
					t.put("code", type.get("code"));
					t.put("type", type.get("type"));
					t.put("unit", type.get("unit"));
					r.put("production_product_type", t);
				}
			}
			out.computeIfAbsent(row.longValue("production_line_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

}
