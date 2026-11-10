package com.olivamore.api.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Materyal cevabi (tank, stok hareketi ve uretim ekranlari). {@link Optional} alanlar yalnizca istenen
 * iliskilerde var: yuklu degilse alan {@code null} ve anahtar yazilmaz, yuklu ama yoksa {@code null} yazilir.
 */
public record MaterialResponse(
	Long id,
	Long productionBatchId,
	Long productionProductTypeId,
	Long parentMaterialId,
	String originType,
	BigDecimal quantity,
	String unit,
	String status,
	String currentLocationType,
	Long currentLocationId,
	Long sourceProductionStageId,
	Long createdByBatchProcessId,
	Long closedByBatchProcessId,
	String note,
	ProductType productionProductType,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Optional<Batch> productionBatch,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Optional<Parent> parentMaterial,
	List<AttributeValue> attributeValues,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Optional<Process> createdByBatchProcess,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Optional<Process> closedByBatchProcess,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public record ProductType(Long id, Long productionProductId, String name, String code, String type, String unit,
		Number densityKgPerLitre, Product productionProduct) {
	}

	public record Product(Long id, String key, String name, String description) {
	}

	public record Batch(Long id, String batchCode, String status) {
	}

	public record Parent(Long id, Long productionProductTypeId,
		BigDecimal quantity, String unit, String status) {
	}

	public record Process(Long id, Long productionBatchId, Long productionStageId, String status) {
	}

	public record AttributeValue(
		Long id,
		Long materialId,
		Long productionProductAttributeDefinitionId,
		Boolean valueBoolean,
		Long valueInteger,
		BigDecimal valueDecimal,
		String valueText,
		Object valueJson,
		Definition definition,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	}

	public record Definition(Long id, Long productionProductId, String attributeKey, String label, String dataType,
		String unit, Boolean isRequired, Integer sortOrder, String description) {
	}

	/** Tank ozeti. */
	public record UnitSummary(Long id, String name, String code) {
	}

}
