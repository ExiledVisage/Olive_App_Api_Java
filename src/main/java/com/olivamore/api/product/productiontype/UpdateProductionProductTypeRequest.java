package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code densityKgPerLitre} ise {@code null} ile temizlenebilir. */
record UpdateProductionProductTypeRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String code,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String type,

	ProductUnit unit,

	Long productionProductId,

	Patch<@DecimalMin(value = "0.0001", message = MessageKeys.MIN)
		@DecimalMax(value = "25", message = MessageKeys.MAX) BigDecimal> densityKgPerLitre) {

	UpdateProductionProductTypeRequest {
		name = Texts.trim(name);
		code = Texts.trim(code);
		type = Texts.trim(type);
	}

}
