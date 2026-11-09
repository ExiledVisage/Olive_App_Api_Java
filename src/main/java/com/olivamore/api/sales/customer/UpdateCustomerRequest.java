package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateCustomerRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	Patch<@Size(max = 255, message = LaravelMessages.MAX_255) String> companyName,

	Patch<@Size(max = 30, message = LaravelMessages.MAX_30) String> phone,

	Patch<@Email(message = LaravelMessages.EMAIL)
		@Size(max = 255, message = LaravelMessages.MAX_255) String> email,

	Patch<String> notes,

	Boolean isActive) {
}
