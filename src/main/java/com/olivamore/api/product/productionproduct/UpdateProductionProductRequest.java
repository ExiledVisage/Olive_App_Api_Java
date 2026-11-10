package com.olivamore.api.product.productionproduct;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateProductionProductRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String key,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Patch<String> description,

	Patch<@DecimalMin(value = "0.0001", message = MessageKeys.MIN)
		@DecimalMax(value = "25", message = MessageKeys.MAX) BigDecimal> defaultDensityKgPerLitre) {

	UpdateProductionProductRequest {
		key = Texts.trim(key);
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
