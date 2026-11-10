package com.olivamore.api.productionline.line;

import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.productionline.ProductionLineResponse;
import com.olivamore.api.productionline.ProductionLineResponse.LineProductResponse;
import com.olivamore.api.productionline.ProductionLineResponse.ProductSummary;
import com.olivamore.api.productionline.ProductionLineResponse.ProductTypeSummary;
import com.olivamore.api.productionline.ProductionStageResponse;
import com.olivamore.api.productionline.ProductCatalog;
import com.olivamore.api.productionline.stage.ProductionStageResponses;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Entity'den API cevabina. Iliskiler listede tek sorguda yuklenir. */
@Component
public class ProductionLineResponses {

	private final ProductionLineRepository lines;
	private final LineInputProductRepository inputs;
	private final LineOutputProductRepository outputs;
	private final ProductionStageResponses stages;
	private final ProductCatalog catalog;

	ProductionLineResponses(ProductionLineRepository lines, LineInputProductRepository inputs,
		LineOutputProductRepository outputs, ProductionStageResponses stages, ProductCatalog catalog) {
		this.lines = lines;
		this.inputs = inputs;
		this.outputs = outputs;
		this.stages = stages;
		this.catalog = catalog;
	}

	@Transactional(readOnly = true)
	public List<ProductionLineResponse> ofProduct(long productId) {
		return ProductionLineResources.merge(of(lines.withInputProduct(productId), false),
			of(lines.withOutputProduct(productId), false));
	}

	@Transactional(readOnly = true)
	public List<ProductionLineResponse> usingProductType(long productId, long typeId) {
		return ProductionLineResources.merge(withInputType(productId, typeId), withOutputType(productId, typeId))
			.stream().sorted(Comparator.comparing(ProductionLineResponse::id)).toList();
	}

	@Transactional(readOnly = true)
	public List<ProductionLineResponse> withInputType(long productId, long typeId) {
		return of(lines.withInputType(productId, typeId), false);
	}

	@Transactional(readOnly = true)
	public List<ProductionLineResponse> withOutputType(long productId, long typeId) {
		return of(lines.withOutputType(productId, typeId), false);
	}

	@Transactional(readOnly = true)
	List<ProductionLineResponse> withExtensions(List<ProductionLine> items) {
		return of(items, true);
	}

	@Transactional(readOnly = true)
	ProductionLineResponse withExtensions(ProductionLine line) {
		return of(List.of(line), true).getFirst();
	}

	private List<ProductionLineResponse> of(List<ProductionLine> items, boolean extensions) {
		List<Long> ids = items.stream().map(ProductionLine::getId).toList();
		Map<Long, List<LineProductResponse>> inputsByLine =
			group(inputs.findByProductionLineIdInOrderBySortOrderAscIdAsc(ids));
		Map<Long, List<LineProductResponse>> outputsByLine =
			group(outputs.findByProductionLineIdInOrderBySortOrderAscIdAsc(ids));
		Map<Long, List<ProductionStageResponse>> stagesByLine = stages.byLine(ids, extensions);
		return items.stream().map(line -> new ProductionLineResponse(line.getId(), line.getName(),
			line.getDescription(), inputsByLine.getOrDefault(line.getId(), List.of()),
			outputsByLine.getOrDefault(line.getId(), List.of()), stagesByLine.getOrDefault(line.getId(), List.of()),
			line.getCreatedAt(), line.getUpdatedAt())).toList();
	}

	private Map<Long, List<LineProductResponse>> group(List<? extends LineProduct> rows) {
		Map<Long, ProductCatalog.Product> products = catalog.products(rows.stream()
			.map(LineProduct::getProductionProductId).toList());
		Map<Long, ProductCatalog.ProductType> types = catalog.productTypes(rows.stream()
			.map(LineProduct::getProductionProductTypeId).toList());
		return rows.stream().map(row -> {
			ProductCatalog.Product product = products.get(row.getProductionProductId());
			Long typeId = row.getProductionProductTypeId();
			ProductCatalog.ProductType type = typeId == null ? null : types.get(typeId);
			return new LineProductResponse(row.getId(), row.getProductionLineId(), row.getProductionProductId(),
				row.getProductionProductTypeId(), row.getSortOrder(),
				new ProductSummary(product.id(), product.key(), product.name()),
				type == null ? null
					: new ProductTypeSummary(type.id(), type.name(), type.code(), type.type(), type.unit()));
		}).collect(Collectors.groupingBy(LineProductResponse::productionLineId));
	}

}
