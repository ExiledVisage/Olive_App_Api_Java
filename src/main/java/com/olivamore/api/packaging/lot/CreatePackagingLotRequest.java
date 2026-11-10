package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

/** Mal girisi. */
record CreatePackagingLotRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String lotCode,

	@NotNull(message = MessageKeys.REQUIRED)
	@Min(value = 1, message = MessageKeys.MIN)
	Integer quantity,

	@JsonDeserialize(using = IsoDateTimeDeserializer.class)
	LocalDateTime receivedAt,

	Long productSupplierId,

	String note) {

	CreatePackagingLotRequest {
		lotCode = Texts.trim(lotCode);
		note = Texts.trim(note);
	}

}
