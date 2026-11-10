package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen {@code batch_code} ve {@code status} degismez; digerleri {@code null} ile temizlenebilir. */
record UpdateProductionBatchRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String batchCode,

	BatchStatus status,

	Patch<@DecimalMin(value = "0", message = MessageKeys.MIN) BigDecimal> quantityKg,

	Patch<Long> supplierId,

	Patch<Long> supplierFieldId,

	Patch<String> receivedAt,

	Patch<String> startedAt,

	Patch<String> completedAt,

	Patch<String> note) {

	UpdateProductionBatchRequest {
		batchCode = Texts.trim(batchCode);
		note = Texts.trim(note);
	}

}
