package com.olivamore.api.inventory.storagemovement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Hareket cevabi; {@code storage_unit} yalnizca yazma cevaplarinda var. */
public record StorageMovementResponse(
	Long id,
	Long storageUnitId,
	Long materialId,
	Long sourceMaterialId,
	Long batchProcessId,
	Long finishedProductRunId,
	Long counterpartyStorageUnitId,
	MovementSource sourceType,
	MovementType movementType,
	String transferReference,
	BigDecimal quantity,
	MaterialUnit unit,
	String note,
	LocalDateTime movedAt,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Optional<MaterialResponse.UnitSummary> storageUnit,
	MaterialResponse material,
	MaterialResponse.Process batchProcess,
	MaterialResponse.UnitSummary counterpartyStorageUnit,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	/**
	 * Tank islem gecmisi satiri (defter): hareketin butun alanlari ve defter alanlari.
	 * @param direction {@code in} ya da {@code out}
	 * @param balanceAfter hareketten sonra bu materyalin bu tanktaki miktari (materyalin biriminde)
	 * @param source giriste tanka giren materyal (on dinlendirmede materyalin kendisi) ve partisi; cikista null
	 * @param finishedProductRun dolum cikisinda kosu; yoksa null
	 * @param counterpartyMaterialId transferde karsi tanktaki materyal; yoksa null
	 * @param mixAfter giristen sonra tank materyalinin sayisal attribute degerleri; cikista null
	 */
	public record HistoryEntry(@JsonUnwrapped StorageMovementResponse movement, String direction,
		BigDecimal balanceAfter, Source source, Run finishedProductRun, Long counterpartyMaterialId,
		List<MixValue> mixAfter) {
	}

	/** Giriste gelen materyal: parti, teslimat ve asama (partiden gelmiyorsa bos). */
	public record Source(Long materialId, Long batchId, String batchCode, String deliveryBatchCode,
		String stageName) {
	}

	public record Run(Long id, String productName, Integer quantity, String status) {
	}

	public record MixValue(Long definitionId, String attributeKey, String label, String unit, BigDecimal value) {
	}

	/**
	 * Defter ozeti: materyal (guncel attribute degerleriyle) ve bu tanktaki hareketlerinin toplamlari.
	 * @param balance hareketlere gore kalan (materyalin biriminde)
	 */
	public record LedgerSummary(MaterialResponse material, BigDecimal totalIn, BigDecimal totalOut,
		BigDecimal balance, int inCount, int outCount, LocalDateTime firstMovedAt, LocalDateTime lastMovedAt) {
	}

	/** Transfer cevabi (200): son tanktan cikista karisimdaki her tip icin bir cikis ve bir giris hareketi. */
	public record Transfer(String transferReference, List<StorageMovementResponse> outMovements,
		List<StorageMovementResponse> inMovements) {
	}

	/**
	 * Bosaltma cevabi (200): her hedef ve parti materyali icin bir cikis ve bir giris hareketi; tanka giren ile
	 * cekilen arasindaki farktan acilan tortu materyalleri (parti basina).
	 */
	public record Emptying(String transferReference, List<StorageMovementResponse> outMovements,
		List<StorageMovementResponse> inMovements, List<MaterialResponse> residueMaterials) {
	}

}
