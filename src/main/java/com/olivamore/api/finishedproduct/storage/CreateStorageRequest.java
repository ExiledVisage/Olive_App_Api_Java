package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code is_active} verilmezse aktif. */
record CreateStorageRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	String description,

	Boolean isActive) {
}
