package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.process.StageExtensionTypeFieldResponse;
import com.olivamore.api.process.StageExtensionTypeSummary;
import com.olivamore.api.productionline.StageExtensionResponse;
import com.olivamore.api.productionline.StageExtensionResponse.LinkedProduct;
import com.olivamore.api.productionline.StageExtensionResponse.ProductSummary;
import com.olivamore.api.productionline.StageExtensionResponse.Value;
import com.olivamore.api.productionline.catalog.ProductCatalog;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Entity'den API cevabina. */
@Component
public class StageExtensionResponses {

	private final ExtensionRepository extensions;
	private final ExtensionValueRepository values;
	private final ExtensionInputProductRepository inputs;
	private final ExtensionOutputProductRepository outputs;
	private final ProcessResources process;
	private final ProductCatalog catalog;

	StageExtensionResponses(ExtensionRepository extensions, ExtensionValueRepository values,
		ExtensionInputProductRepository inputs, ExtensionOutputProductRepository outputs,
		ProcessResources process, ProductCatalog catalog) {
		this.extensions = extensions;
		this.values = values;
		this.inputs = inputs;
		this.outputs = outputs;
		this.process = process;
		this.catalog = catalog;
	}

	/**
	 * Asamaya gore, sort_order sirasinda.
	 * @param withValues {@code false} ise cevapta {@code values} yok
	 */
	@Transactional(readOnly = true)
	public Map<Long, List<StageExtensionResponse>> byStage(List<Long> stageIds, boolean withValues) {
		List<ProductionStageExtension> rows = extensions.findByProductionStageIdInOrderBySortOrderAscIdAsc(stageIds);
		List<Long> ids = rows.stream().map(ProductionStageExtension::getId).toList();
		Map<Long, StageExtensionTypeSummary> types = process.extensionTypesById(
			rows.stream().map(ProductionStageExtension::getStageExtensionTypeId).toList());
		Map<Long, List<Value>> valuesByExtension = withValues ? values(ids) : Map.of();
		Map<Long, List<LinkedProduct>> inputsByExtension =
			products(inputs.findByProductionStageExtensionIdInOrderBySortOrderAscIdAsc(ids));
		Map<Long, List<LinkedProduct>> outputsByExtension =
			products(outputs.findByProductionStageExtensionIdInOrderBySortOrderAscIdAsc(ids));
		return rows.stream().map(row -> new StageExtensionResponse(row.getId(), row.getProductionStageId(),
			row.getStageExtensionTypeId(), row.isRequired(), row.isEnabled(), row.getSortOrder(),
			types.get(row.getStageExtensionTypeId()),
			withValues ? valuesByExtension.getOrDefault(row.getId(), List.of()) : null,
			inputsByExtension.getOrDefault(row.getId(), List.of()),
			outputsByExtension.getOrDefault(row.getId(), List.of())))
			.collect(Collectors.groupingBy(StageExtensionResponse::productionStageId));
	}

	private Map<Long, List<Value>> values(List<Long> extensionIds) {
		List<ProductionStageExtensionValue> rows =
			values.findByProductionStageExtensionIdInOrderByIdAsc(extensionIds);
		Map<Long, StageExtensionTypeFieldResponse> fields = process.extensionTypeFieldsById(
			rows.stream().map(ProductionStageExtensionValue::getStageExtensionTypeFieldId).toList());
		return rows.stream().map(row -> new Value(row.getId(), row.getProductionStageExtensionId(),
			row.getStageExtensionTypeFieldId(), row.getValueBoolean(), row.getValueInteger(), row.getValueDecimal(),
			row.getValueText(), row.getValueJson(), fields.get(row.getStageExtensionTypeFieldId())))
			.collect(Collectors.groupingBy(Value::productionStageExtensionId));
	}

	private Map<Long, List<LinkedProduct>> products(List<? extends ExtensionProduct> rows) {
		Map<Long, ProductCatalog.Product> products =
			catalog.products(rows.stream().map(ExtensionProduct::getProductionProductId).toList());
		return rows.stream().map(row -> {
			ProductCatalog.Product product = products.get(row.getProductionProductId());
			return new LinkedProduct(row.getId(), row.getProductionStageExtensionId(), row.getProductionProductId(),
				row.getSortOrder(), row.isRequired(),
				new ProductSummary(product.id(), product.key(), product.name(), product.description()));
		}).collect(Collectors.groupingBy(LinkedProduct::productionStageExtensionId));
	}

}
