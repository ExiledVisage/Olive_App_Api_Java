package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.json.FlexibleDateTimeDeserializer;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdatePackagingLotRequest(
	@Size(max = 100, message = LaravelMessages.MAX_100)
	String lotCode,

	@JsonDeserialize(using = FlexibleDateTimeDeserializer.class)
	LocalDateTime receivedAt,

	Patch<Long> productSupplierId,

	Patch<String> note) {
}
