package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.json.FlexibleDateTimeDeserializer;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

/** Mal girisi. */
record CreatePackagingLotRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 100, message = LaravelMessages.MAX_100)
	String lotCode,

	@NotNull(message = LaravelMessages.REQUIRED)
	@Min(value = 1, message = LaravelMessages.MIN_1)
	Integer quantity,

	@JsonDeserialize(using = FlexibleDateTimeDeserializer.class)
	LocalDateTime receivedAt,

	Long productSupplierId,

	String note) {
}
