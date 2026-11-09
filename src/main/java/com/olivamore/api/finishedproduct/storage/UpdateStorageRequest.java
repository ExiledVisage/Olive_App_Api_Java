package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code description} {@code null} ile temizlenir. */
record UpdateStorageRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	Patch<String> description,

	Boolean isActive) {
}
