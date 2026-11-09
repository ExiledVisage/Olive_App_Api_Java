package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code densityKgPerLitre} ise {@code null} ile temizlenebilir. */
record UpdateProductionProductTypeRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String code,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String type,

	ProductUnit unit,

	Long productionProductId,

	Patch<@DecimalMin(value = "0.0001", message = LaravelMessages.MIN_DENSITY)
		@DecimalMax(value = "25", message = LaravelMessages.MAX_DENSITY) BigDecimal> densityKgPerLitre) {
}
