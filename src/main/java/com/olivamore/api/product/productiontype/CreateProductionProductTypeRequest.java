package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateProductionProductTypeRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String code,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String type,

	@NotNull(message = MessageKeys.REQUIRED)
	ProductUnit unit,

	@NotNull(message = MessageKeys.REQUIRED)
	Long productionProductId,

	@DecimalMin(value = "0.0001", message = MessageKeys.MIN)
	@DecimalMax(value = "25", message = MessageKeys.MAX)
	BigDecimal densityKgPerLitre) {

	CreateProductionProductTypeRequest {
		name = Texts.trim(name);
		code = Texts.trim(code);
		type = Texts.trim(type);
	}

}
