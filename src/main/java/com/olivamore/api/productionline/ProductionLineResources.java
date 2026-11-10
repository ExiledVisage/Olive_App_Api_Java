package com.olivamore.api.productionline;

import com.olivamore.api.productionline.line.ProductionLineResponses;
import com.olivamore.api.productionline.stageextension.StageExtensionResponses;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Uretim hatti cevaplarinin diger modullere acik kismi. */
@Component
public class ProductionLineResources {

	private final ProductionLineResponses lines;
	private final StageExtensionResponses extensions;
	private final JdbcClient jdbc;

	ProductionLineResources(ProductionLineResponses lines, StageExtensionResponses extensions, JdbcClient jdbc) {
		this.lines = lines;
		this.extensions = extensions;
		this.jdbc = jdbc;
	}

	public record Stage(Long id, Long productionLineId, Long processTypeId, String name, int stageOrder,
		boolean isRequired, boolean requiresApproval) {
	}

	public record Line(Long id, String name, String description) {
	}

	public record LineProduct(Long id, Long productionLineId, Long productionProductId, Long productionProductTypeId,
		int sortOrder) {
	}

	/** Bir urun kategorisini kullanan hat ve uzanti kayitlari. */
	public record ProductUse(long lineInputs, long lineOutputs, long extensionInputs, long extensionOutputs) {
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, Stage> stages(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select * from production_stages where id in (:ids)").param("ids", present)
			.query(Stage.class).list().stream().collect(Collectors.toMap(Stage::id, Function.identity()));
	}

	/** Hattin asamalari sirayla. */
	public List<Long> stageIds(long lineId) {
		return jdbc.sql("select id from production_stages where production_line_id = :line order by stage_order asc")
			.param("line", lineId).query(Long.class).list();
	}

	public int lastStageOrder(long lineId) {
		return jdbc.sql("select max(stage_order) from production_stages where production_line_id = :line")
			.param("line", lineId).query(Integer.class).single();
	}

	public boolean lineExists(long id) {
		return jdbc.sql("select count(*) from production_lines where id = :id").param("id", id)
			.query(Long.class).single() > 0;
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, Line> lines(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, description from production_lines where id in (:ids)").param("ids", present)
			.query(Line.class).list().stream().collect(Collectors.toMap(Line::id, Function.identity()));
	}

	/** Hatlarin girdileri, sort_order sirasinda. */
	public List<LineProduct> lineInputs(Collection<Long> lineIds) {
		return lineProducts("production_line_input_production_products", lineIds);
	}

	/** Hatlarin ciktilari, sort_order sirasinda. */
	public List<LineProduct> lineOutputs(Collection<Long> lineIds) {
		return lineProducts("production_line_output_production_products", lineIds);
	}

	private List<LineProduct> lineProducts(String table, Collection<Long> lineIds) {
		if (lineIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select * from " + table + " where production_line_id in (:ids) order by sort_order asc")
			.param("ids", lineIds).query(LineProduct.class).list();
	}

	/** Silme kontrolu icin. */
	public ProductUse productUse(long productId) {
		return new ProductUse(count("production_line_input_production_products", productId),
			count("production_line_output_production_products", productId),
			count("production_stage_extension_input_production_products", productId),
			count("production_stage_extension_output_production_products", productId));
	}

	private long count(String table, long productId) {
		return jdbc.sql("select count(*) from " + table + " where production_product_id = ?").param(productId)
			.query(Long.class).single();
	}

	/** Bir urun kategorisinin girdi ve cikti hatlari, girdiler once, tekrarsiz. */
	public List<ProductionLineResponse> ofProduct(long productId) {
		return lines.ofProduct(productId);
	}

	/** Tipin kategorisini (tip ozelinde ya da tip belirtmeden) girdi ya da cikti olarak kullanan hatlar. */
	public List<ProductionLineResponse> usingProductType(long productId, long typeId) {
		return lines.usingProductType(productId, typeId);
	}

	public List<ProductionLineResponse> withInputType(long productId, long typeId) {
		return lines.withInputType(productId, typeId);
	}

	public List<ProductionLineResponse> withOutputType(long productId, long typeId) {
		return lines.withOutputType(productId, typeId);
	}

	/** Iki hat listesi, ilkinin sirasiyla ve id'ye gore tekrarsiz. */
	public static List<ProductionLineResponse> merge(List<ProductionLineResponse> first,
		List<ProductionLineResponse> second) {
		Map<Long, ProductionLineResponse> unique = new LinkedHashMap<>();
		first.forEach(line -> unique.putIfAbsent(line.id(), line));
		second.forEach(line -> unique.putIfAbsent(line.id(), line));
		return new ArrayList<>(unique.values());
	}

	/** Asamaya gore, sort_order sirasinda. */
	public Map<Long, List<StageExtensionResponse>> stageExtensions(List<Long> stageIds, boolean withValues) {
		return extensions.byStage(stageIds, withValues);
	}

}
