package com.olivamore.api.supplier;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateSupplierFieldRequest(
	@Size(max = 50, message = LaravelMessages.MAX_50)
	String code,

	Patch<@Size(max = 255, message = LaravelMessages.MAX_255) String> location,

	Patch<@DecimalMin(value = "0", message = LaravelMessages.MIN_0) BigDecimal> areaDecare,

	Patch<String> note,

	Boolean isActive) {
}
