package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code is_active} verilmezse aktif. */
record CreateStorageRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	String description,

	Boolean isActive) {

	CreateStorageRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
