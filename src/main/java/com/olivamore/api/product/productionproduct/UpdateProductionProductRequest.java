package com.olivamore.api.product.productionproduct;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateProductionProductRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String key,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	Patch<String> description,

	Patch<@DecimalMin(value = "0.0001", message = LaravelMessages.MIN_DENSITY)
		@DecimalMax(value = "25", message = LaravelMessages.MAX_DENSITY) BigDecimal> defaultDensityKgPerLitre) {
}
