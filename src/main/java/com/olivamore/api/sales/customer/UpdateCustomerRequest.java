package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateCustomerRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Patch<@Size(max = 255, message = MessageKeys.SIZE_MAX) String> companyName,

	Patch<@Size(max = 30, message = MessageKeys.SIZE_MAX) String> phone,

	Patch<@Email(message = MessageKeys.EMAIL)
		@Size(max = 255, message = MessageKeys.SIZE_MAX) String> email,

	Patch<String> notes,

	Boolean isActive) {

	UpdateCustomerRequest {
		name = Texts.trim(name);
		companyName = Texts.trim(companyName);
		email = Texts.trim(email);
		phone = Texts.trim(phone);
		notes = Texts.trim(notes);
	}

}
