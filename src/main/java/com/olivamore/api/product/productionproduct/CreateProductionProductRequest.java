package com.olivamore.api.product.productionproduct;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateProductionProductRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String key,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	String description,

	@DecimalMin(value = "0.0001", message = LaravelMessages.MIN_DENSITY)
	@DecimalMax(value = "25", message = LaravelMessages.MAX_DENSITY)
	BigDecimal defaultDensityKgPerLitre) {
}
