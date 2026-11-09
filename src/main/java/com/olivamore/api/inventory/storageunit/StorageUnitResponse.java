package com.olivamore.api.inventory.storageunit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Tank cevabi: kapasite metrikleri, izinli urun/tipler, kurallar, bakiyeler ve icindeki aktif materyaller. */
record StorageUnitResponse(
	Long id,
	String name,
	String code,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	BigDecimal capacity,
	MaterialUnit capacityUnit,
	boolean isBlendTank,
	boolean allowMultipleProductionProductTypes,
	boolean allowMultipleProductionProducts,
	String description,
	boolean isActive,
	Number filledQuantity,
	Number remainingCapacity,
	Number filledPercentage,
	Number emptyPercentage,
	List<AllowedProduct> allowedProductionProducts,
	List<AllowedType> allowedProductionProductTypes,
	List<AttributeRule> attributeRules,
	List<Balance> balances,
	List<MaterialResponse> currentMaterials,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Product(Long id, String key, String name, String description) {
	}

	record AllowedProduct(Long id, Long storageUnitId, Long productionProductId, Product productionProduct,
		LocalDateTime createdAt, LocalDateTime updatedAt) {
	}

	/** Tipin kategorisi yuklenmiyor ({@code production_product} her zaman null). */
	record AllowedType(Long id, Long storageUnitId, Long productionProductTypeId, TypeSummary productionProductType,
		LocalDateTime createdAt, LocalDateTime updatedAt) {
	}

	record TypeSummary(Long id, Long productionProductId, String name, String code, String type, String unit,
		Product productionProduct) {
	}

	record AttributeRule(
		Long id,
		Long storageUnitId,
		Long productionProductAttributeDefinitionId,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal minValueDecimal,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal maxValueDecimal,
		ApplyMode applyMode,
		boolean isActive,
		String note,
		MaterialResponse.Definition definition,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	}

	record Balance(
		Long id,
		Long storageUnitId,
		Long productionProductId,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal currentQuantity,
		MaterialUnit unit,
		Product productionProduct,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	}

}
