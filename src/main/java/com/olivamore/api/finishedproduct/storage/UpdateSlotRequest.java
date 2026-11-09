package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; ad ve urun kilidi {@code null} ile temizlenir. */
record UpdateSlotRequest(
	Patch<@Size(max = 50, message = LaravelMessages.MAX_50) String> name,

	@DecimalMin(value = "0.001", message = LaravelMessages.MIN_VOLUME)
	BigDecimal volumeLitre,

	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer maxQuantity,

	Patch<Long> finishedProductId) {
}
