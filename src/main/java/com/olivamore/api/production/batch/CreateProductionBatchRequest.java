package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Yeni parti: {@code status} verilmezse pending, {@code received_at} verilmezse simdi. Tarihler serviste. */
record CreateProductionBatchRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String batchCode,

	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<Long> inputProductionProductTypeIds,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long productionLineId,

	BatchStatus status,

	@DecimalMin(value = "0", message = LaravelMessages.MIN_0)
	BigDecimal quantityKg,

	Long supplierId,

	Long supplierFieldId,

	String receivedAt,

	String startedAt,

	String completedAt,

	String note) {
}
