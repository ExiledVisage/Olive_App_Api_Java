package com.olivamore.api.supplier;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateSupplierFieldRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String code,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String location,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal areaDecare,

	String note,

	Boolean isActive) {

	CreateSupplierFieldRequest {
		code = Texts.trim(code);
		location = Texts.trim(location);
		note = Texts.trim(note);
	}

}
