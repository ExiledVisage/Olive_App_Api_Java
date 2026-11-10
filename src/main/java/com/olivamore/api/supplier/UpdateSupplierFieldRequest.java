package com.olivamore.api.supplier;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateSupplierFieldRequest(
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String code,

	Patch<@Size(max = 255, message = MessageKeys.SIZE_MAX) String> location,

	Patch<@DecimalMin(value = "0", message = MessageKeys.MIN) BigDecimal> areaDecare,

	Patch<String> note,

	Boolean isActive) {

	UpdateSupplierFieldRequest {
		code = Texts.trim(code);
		location = Texts.trim(location);
		note = Texts.trim(note);
	}

}
