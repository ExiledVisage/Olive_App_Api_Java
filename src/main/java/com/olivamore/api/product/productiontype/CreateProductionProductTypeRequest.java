package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateProductionProductTypeRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String code,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String type,

	@NotNull(message = LaravelMessages.REQUIRED)
	ProductUnit unit,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long productionProductId,

	@DecimalMin(value = "0.0001", message = LaravelMessages.MIN_DENSITY)
	@DecimalMax(value = "25", message = LaravelMessages.MAX_DENSITY)
	BigDecimal densityKgPerLitre) {
}
