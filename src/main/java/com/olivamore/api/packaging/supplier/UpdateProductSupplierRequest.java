package com.olivamore.api.packaging.supplier;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateProductSupplierRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@Size(max = 50, message = LaravelMessages.MAX_50)
	String code,

	Patch<@Size(max = 50, message = LaravelMessages.MAX_50) String> phone,

	Patch<@Email(message = LaravelMessages.EMAIL)
		@Size(max = 255, message = LaravelMessages.MAX_255) String> email,

	Patch<String> address,

	Patch<String> note,

	Boolean isActive) {
}
