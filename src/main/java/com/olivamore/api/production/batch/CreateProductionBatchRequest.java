package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yeni parti: {@code status} verilmezse pending, {@code received_at} verilmezse simdi. Tarihler serviste. Plaka
 * teslimatin sabit bilgisi, zorunlu.
 */
record CreateProductionBatchRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String batchCode,

	@NotEmpty(message = MessageKeys.REQUIRED)
	List<Long> inputProductionProductTypeIds,

	@NotNull(message = MessageKeys.REQUIRED)
	Long productionLineId,

	BatchStatus status,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal quantityKg,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String truckPlate,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String weighingSlipNo,

	Long supplierId,

	Long supplierFieldId,

	String receivedAt,

	String startedAt,

	String completedAt,

	String note) {

	CreateProductionBatchRequest {
		batchCode = Texts.trim(batchCode);
		truckPlate = Texts.trim(truckPlate);
		weighingSlipNo = Texts.trim(weighingSlipNo);
		note = Texts.trim(note);
	}

}
