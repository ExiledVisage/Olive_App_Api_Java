package com.olivamore.api.finishedproduct.run;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.finishedproduct.RecipeResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Kosunun API cevabi. {@code source_storage_unit} yukluyse {@link Optional} (tanki silinmis kosuda
 * {@code null} yazilir), yuklu degilse alan {@code null} ve anahtar yok.
 */
record FinishedProductRunResponse(
	Long id,
	int quantity,
	RunStatus status,
	List<RunStatus> allowedTransitions,
	String note,
	LocalDateTime startedAt,
	LocalDateTime completedAt,
	Product finishedProduct,
	RecipeResponse.Summary recipe,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Optional<Unit> sourceStorageUnit,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Material> materials,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Packaging> packagings,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<AttributeValue> attributeValues,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	PackagingScrap packagingScrap,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Product(Long id, String name, BigDecimal volumeLitre) {
	}

	record Unit(Long id, String name, String code) {
	}

	record Material(
		Long id,
		Long sourceMaterialId,
		BigDecimal quantityUsed,
		String unit,
		Long productionProductTypeId,
		String productionProductTypeName,
		Double densityKgPerLitre) {
	}

	record Packaging(
		Long id,
		Long packagingItemId,
		String packagingItemName,
		String packagingItemType,
		Long packagingLotId,
		String lotCode,
		LocalDateTime lotReceivedAt,
		Integer lotRemaining,
		String supplierName,
		int quantity) {
	}

	/** Dolum firesi: kayitlar ve kalem basina toplam ile oran (fire / (kullanilan + fire) x 100). */
	record PackagingScrap(List<Scrap> entries, List<ScrapTotal> items) {
	}

	record Scrap(Long id, Long packagingItemId, String packagingItemName, Long packagingLotId, String lotCode,
		int quantity, String reason, String note, LocalDateTime movedAt, Long userId) {
	}

	record ScrapTotal(Long packagingItemId, String packagingItemName, long used, long scrapped,
		BigDecimal scrapRatePercent) {
	}

	record AttributeValue(
		Long id,
		Long attributeDefinitionId,
		String attributeLabel,
		BigDecimal estimatedValue,
		BigDecimal actualValue) {
	}

}
