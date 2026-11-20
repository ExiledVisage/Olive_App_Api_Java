package com.olivamore.api.production.batch;

import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.production.batchprocess.BatchProcessResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Parti: hat, giris/cikis tipleri, prosesler ve materyallerle. {@code allocated_kg}, {@code remaining_kg} ve
 * {@code groups} yalniz bolunebilen hattaki teslimatta dolu; {@code parent_batch_code} yalniz grupta.
 */
record ProductionBatchResponse(
	Long id,
	Long parentBatchId,
	String parentBatchCode,
	String batchCode,
	Long productionLineId,
	Long supplierId,
	Long supplierFieldId,
	BatchQueries.Supplier supplier,
	BatchQueries.SupplierField supplierField,
	Long createdByUserId,
	BatchStatus status,
	BigDecimal quantityKg,
	BigDecimal allocatedKg,
	BigDecimal remainingKg,
	String truckPlate,
	String weighingSlipNo,
	LocalDateTime receivedAt,
	LocalDateTime startedAt,
	LocalDateTime completedAt,
	String note,
	List<BatchType> inputProductionProductTypes,
	List<BatchType> outputProductionProductTypes,
	Line productionLine,
	BatchProcessResponse.UserSummary createdByUser,
	List<BatchProcessResponse> processes,
	List<MaterialResponse> materials,
	List<Group> groups,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	/** Teslimatin grubu; {@code current_stage} ilk bitmemis asama, hepsi bittiyse bos. */
	/** {@code splitStageName}: grubun ayrildigi an teslimatin son biten asamasi; kantardan once ayrildiysa null. */
	record Group(Long id, String batchCode, BigDecimal quantityKg, BatchStatus status, LocalDateTime startedAt,
		LocalDateTime completedAt, CurrentStage currentStage, String splitStageName) {
	}

	record CurrentStage(Long productionStageId, String name, String status) {
	}

	record BatchType(Long id, Long productionBatchId, Long productionProductTypeId, int sortOrder,
		BatchQueries.ProductType productionProductType) {
	}

	record Line(Long id, String name, String description, List<LineProduct> inputProductionProducts,
		List<LineProduct> outputProductionProducts) {
	}

	record LineProduct(Long id, Long productionLineId, Long productionProductId, int sortOrder,
		BatchQueries.Product productionProduct) {
	}

}
