package com.olivamore.api.process.processtype;

import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code description} ise {@code null} ile temizlenebilir. */
record UpdateProcessTypeRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	Patch<String> description,

	ProcessCategory category,

	Boolean isActive) {
}
