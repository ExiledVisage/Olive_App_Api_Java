package com.olivamore.api.productionline;

import com.olivamore.api.productionline.line.ProductionLineResource;
import com.olivamore.api.productionline.stageextension.ProductionStageExtensionResource;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uretim hatti resource'larinin diger modullere acik kismi: urun kategorisi ve tipinin hatlari
 * (ProductionProductResource, ProductionProductTypeController::productionLines/flow) ve asama uzantilari
 * (BatchProcessResource'taki {@code extensions}).
 */
@Component
public class ProductionLineResources {

	private final Db db;
	private final ProductionLineResource lineResource;
	private final ProductionStageExtensionResource extensionResource;

	ProductionLineResources(Db db, ProductionLineResource lineResource, ProductionStageExtensionResource extensionResource) {
		this.db = db;
		this.lineResource = lineResource;
		this.extensionResource = extensionResource;
	}

	/**
	 * Bir urun kategorisinin girdi ve cikti hatlari ({@code belongsToMany}, siralama yok), girdiler once,
	 * tekrarsiz. Her grubun iliskileri Laravel'deki gibi kendi icinde yuklenir.
	 */
	public List<Map<String, Object>> ofProduct(long productId) {
		List<Row> inputs = db.select("""
			select production_lines.* from production_lines
			inner join production_line_input_production_products
			  on production_lines.id = production_line_input_production_products.production_line_id
			where production_line_input_production_products.production_product_id in (%d)""".formatted(productId));
		List<Row> outputs = db.select("""
			select production_lines.* from production_lines
			inner join production_line_output_production_products
			  on production_lines.id = production_line_output_production_products.production_line_id
			where production_line_output_production_products.production_product_id in (%d)""".formatted(productId));
		return merge(basic(inputs), basic(outputs));
	}

	/** Tipin kategorisini (tip ozelinde ya da tip belirtmeden) girdi ya da cikti olarak kullanan hatlar. */
	public List<Map<String, Object>> usingProductType(Row productType) {
		String input = matches("production_line_input_production_products", productType);
		String output = matches("production_line_output_production_products", productType);
		return basic(db.select("select * from production_lines where (" + input + " or " + output + ")"));
	}

	/** Tipin kategorisini girdi olarak kullanan hatlar. */
	public List<Map<String, Object>> withInputType(Row productType) {
		return basic(db.select("select * from production_lines where "
			+ matches("production_line_input_production_products", productType)));
	}

	/** Tipin kategorisini cikti olarak kullanan hatlar. */
	public List<Map<String, Object>> withOutputType(Row productType) {
		return basic(db.select("select * from production_lines where "
			+ matches("production_line_output_production_products", productType)));
	}

	/** Iki hat listesi, ilkinin sirasiyla ve id'ye gore tekrarsiz. */
	public static List<Map<String, Object>> merge(List<Map<String, Object>> first, List<Map<String, Object>> second) {
		Map<Object, Map<String, Object>> unique = new LinkedHashMap<>();
		for (Map<String, Object> line : first) {
			unique.putIfAbsent(line.get("id"), line);
		}
		for (Map<String, Object> line : second) {
			unique.putIfAbsent(line.get("id"), line);
		}
		return new ArrayList<>(unique.values());
	}

	/**
	 * ProductionStageExtensionResource'lar, asamaya gore (sort_order sirasinda): stageExtensionType, girdi ve
	 * cikti urunleri yuklu.
	 * @param withValues {@code values.stageExtensionTypeField} yuklu mu (proses detayinda evet, parti
	 *        baglaminda hayir: anahtar hic yazilmaz)
	 */
	public Map<Long, List<Map<String, Object>>> stageExtensions(List<Long> stageIds, boolean withValues) {
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Map.Entry<Long, List<Map<String, Object>>> entry : extensionResource.byStage(stageIds, false).entrySet()) {
			List<Map<String, Object>> extensions = new ArrayList<>();
			for (Map<String, Object> e : entry.getValue()) {
				Map<String, Object> copy = new LinkedHashMap<>(e);
				if (!withValues) {
					copy.remove("values");
				}
				extensions.add(copy);
			}
			out.put(entry.getKey(), extensions);
		}
		return out;
	}

	/** ProductionProductTypeService::lineRelations (tip ve uzanti yok). */
	private List<Map<String, Object>> basic(List<Row> lines) {
		return lineResource.lines(lines, ProductionLineResource.LineLoad.BASIC);
	}

	/** ProductionProductTypeService::matchesProductType (whereHas): kategori ayni ve tip bos ya da ayni. */
	private static String matches(String pivot, Row type) {
		long category = type.longValue("production_product_id");
		long id = type.id();
		return "exists (select * from " + pivot + " where production_lines.id = " + pivot + ".production_line_id"
			+ " and production_product_id = " + category
			+ " and (production_product_type_id is null or production_product_type_id = " + id + "))";
	}

}
