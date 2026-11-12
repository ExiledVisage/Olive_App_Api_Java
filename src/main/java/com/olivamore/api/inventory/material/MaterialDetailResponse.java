package com.olivamore.api.inventory.material;

import com.olivamore.api.inventory.MaterialResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Materyal detayi: miktar dokumu (giren, ayrilan, kapanan, kalan), ebeveyn ve cocuklar, attribute degerleri
 * (tanim sirasiyla), son 20 hareket, bu materyalden cekilen dolum kosulari.
 */
record MaterialDetailResponse(
	Long id,
	BigDecimal quantity,
	String unit,
	String status,
	String originType,
	String note,
	LocalDateTime createdAt,
	QuantityBreakdown quantityBreakdown,
	TypeSummary productionProductType,
	BatchSummary productionBatch,
	MaterialResponse.UnitSummary storageUnit,
	LineageSummary parentMaterial,
	List<LineageSummary> childMaterials,
	List<AttributeLine> attributeValues,
	List<Movement> movements,
	List<Run> runs) {

	record QuantityBreakdown(Number initial, Number splitOut, Number closed, Number remaining) {
	}

	/** {@code density_kg_per_litre} etkin yogunluk. */
	record TypeSummary(Long id, String name, String unit, Number densityKgPerLitre) {
	}

	record BatchSummary(Long id, String batchCode, String status) {
	}

	record LineageSummary(Long id, String name,
		BigDecimal quantity, String unit, String status) {
	}

	record AttributeLine(String label, String unit, Object value) {
	}

	record Movement(
		Long id,
		Long finishedProductRunId,
		String movementType,
		BigDecimal quantity,
		String unit,
		LocalDateTime movedAt,
		String storageUnitName,
		String counterpartyStorageUnitName,
		Long tankMaterialId) {
	}

	record Run(Long id, String status, String finishedProductName, Integer bottleQuantity, Number quantityUsed,
		LocalDateTime date) {
	}

}
