package com.olivamore.api.supplier;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateSupplierFieldRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 50, message = LaravelMessages.MAX_50)
	String code,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String location,

	@DecimalMin(value = "0", message = LaravelMessages.MIN_0)
	BigDecimal areaDecare,

	String note,

	Boolean isActive) {
}
