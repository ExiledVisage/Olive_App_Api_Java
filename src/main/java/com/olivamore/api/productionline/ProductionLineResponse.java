package com.olivamore.api.productionline;

import java.time.LocalDateTime;
import java.util.List;

/** Uretim hattinin API cevabi. */
public record ProductionLineResponse(
	Long id,
	String name,
	String description,
	List<LineProductResponse> inputProductionProducts,
	List<LineProductResponse> outputProductionProducts,
	List<ProductionStageResponse> stages,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public record LineProductResponse(
		Long id,
		Long productionLineId,
		Long productionProductId,
		Long productionProductTypeId,
		int sortOrder,
		ProductSummary productionProduct,
		ProductTypeSummary productionProductType) {
	}

	public record ProductSummary(Long id, String key, String name) {
	}

	public record ProductTypeSummary(Long id, String name, String code, String type, String unit) {
	}

}
