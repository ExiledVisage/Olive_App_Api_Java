package com.olivamore.api.product.productionproduct;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateProductionProductRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String key,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	String description,

	@DecimalMin(value = "0.0001", message = MessageKeys.MIN)
	@DecimalMax(value = "25", message = MessageKeys.MAX)
	BigDecimal defaultDensityKgPerLitre) {

	CreateProductionProductRequest {
		key = Texts.trim(key);
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
