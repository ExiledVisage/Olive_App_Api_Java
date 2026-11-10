package com.olivamore.api.production.batch;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen {@code batch_code} ve {@code status} degismez; digerleri {@code null} ile temizlenebilir. */
record UpdateProductionBatchRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String batchCode,

	BatchStatus status,

	Patch<@DecimalMin(value = "0", message = LaravelMessages.MIN_0) BigDecimal> quantityKg,

	Patch<Long> supplierId,

	Patch<Long> supplierFieldId,

	Patch<String> receivedAt,

	Patch<String> startedAt,

	Patch<String> completedAt,

	Patch<String> note) {
}
