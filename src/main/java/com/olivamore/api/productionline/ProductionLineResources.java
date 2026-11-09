package com.olivamore.api.productionline;

import com.olivamore.api.productionline.line.ProductionLineResponses;
import com.olivamore.api.productionline.stageextension.StageExtensionResponses;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uretim hatti cevaplarinin diger modullere acik kismi. */
@Component
public class ProductionLineResources {

	private final ProductionLineResponses lines;
	private final StageExtensionResponses extensions;

	ProductionLineResources(ProductionLineResponses lines, StageExtensionResponses extensions) {
		this.lines = lines;
		this.extensions = extensions;
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
