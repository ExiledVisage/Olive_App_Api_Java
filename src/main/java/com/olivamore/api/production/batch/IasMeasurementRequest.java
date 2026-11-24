package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * IAS olcumu: {@code kind} {@code olive} (zeytin) ya da {@code pomace} (pirina). Yag (yas baz) ve nem zorunlu; KPY
 * verilmezse hesaplanir. Asitlik ve endustriyel randiman yalnizca zeytinde. {@code material_id} verilmezse partinin
 * o urundeki materyali.
 */
record IasMeasurementRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	String kind,

	Long materialId,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@DecimalMax(value = "100", message = MessageKeys.MAX)
	BigDecimal oilRatio,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@DecimalMax(value = "100", inclusive = false, message = MessageKeys.MAX)
	BigDecimal moisture,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@DecimalMax(value = "100", message = MessageKeys.MAX)
	BigDecimal kpy,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal acidity,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@DecimalMax(value = "100", message = MessageKeys.MAX)
	BigDecimal industrialYield,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String sampleNo) {

	IasMeasurementRequest {
		kind = Texts.trim(kind);
		sampleNo = Texts.trim(sampleNo);
	}

}
