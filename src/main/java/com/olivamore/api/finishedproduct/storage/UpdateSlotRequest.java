package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; ad ve urun kilidi {@code null} ile temizlenir. */
record UpdateSlotRequest(
	Patch<@Size(max = 50, message = MessageKeys.SIZE_MAX) String> name,

	@DecimalMin(value = "0.001", message = MessageKeys.MIN)
	BigDecimal volumeLitre,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer maxQuantity,

	Patch<Long> finishedProductId) {

	UpdateSlotRequest {
		name = Texts.trim(name);
	}

}
