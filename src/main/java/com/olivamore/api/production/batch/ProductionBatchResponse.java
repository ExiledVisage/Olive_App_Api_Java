package com.olivamore.api.production.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.production.batchprocess.BatchProcessResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Parti: hat, giris/cikis tipleri, prosesler ve materyallerle. */
record ProductionBatchResponse(
	Long id,
	String batchCode,
	Long productionLineId,
	Long supplierId,
	Long supplierFieldId,
	BatchQueries.Supplier supplier,
	BatchQueries.SupplierField supplierField,
	Long createdByUserId,
	BatchStatus status,
	@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantityKg,
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
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

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
