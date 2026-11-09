package com.olivamore.api.productionline;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.process.StageExtensionTypeFieldResponse;
import com.olivamore.api.process.StageExtensionTypeSummary;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/** Asama uzantisinin API cevabi. {@code values} istenmediyse cevapta yok. */
public record StageExtensionResponse(
	Long id,
	Long productionStageId,
	Long stageExtensionTypeId,
	boolean isRequired,
	boolean isEnabled,
	int sortOrder,
	StageExtensionTypeSummary stageExtensionType,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Value> values,
	List<LinkedProduct> inputProductionProducts,
	List<LinkedProduct> outputProductionProducts) {

	public record Value(
		Long id,
		Long productionStageExtensionId,
		Long stageExtensionTypeFieldId,
		Boolean valueBoolean,
		Long valueInteger,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal valueDecimal,
		String valueText,
		JsonNode valueJson,
		StageExtensionTypeFieldResponse field) {
	}

	public record LinkedProduct(
		Long id,
		Long productionStageExtensionId,
		Long productionProductId,
		int sortOrder,
		boolean isRequired,
		ProductSummary productionProduct) {
	}

	public record ProductSummary(Long id, String key, String name, String description) {
	}

}
