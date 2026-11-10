package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdatePackagingLotRequest(
	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String lotCode,

	@JsonDeserialize(using = IsoDateTimeDeserializer.class)
	LocalDateTime receivedAt,

	Patch<Long> productSupplierId,

	Patch<String> note) {

	UpdatePackagingLotRequest {
		lotCode = Texts.trim(lotCode);
		note = Texts.trim(note);
	}

}
