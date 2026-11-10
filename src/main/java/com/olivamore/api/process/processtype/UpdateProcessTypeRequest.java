package com.olivamore.api.process.processtype;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code description} ise {@code null} ile temizlenebilir. */
record UpdateProcessTypeRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Patch<String> description,

	ProcessCategory category,

	Boolean isActive) {

	UpdateProcessTypeRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
