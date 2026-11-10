package com.olivamore.api.supplier;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateSupplierRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String code,

	Patch<@Size(max = 50, message = MessageKeys.SIZE_MAX) String> phone,

	Patch<@Email(message = MessageKeys.EMAIL)
		@Size(max = 255, message = MessageKeys.SIZE_MAX) String> email,

	Patch<String> address,

	Patch<String> note,

	Boolean isActive) {

	UpdateSupplierRequest {
		name = Texts.trim(name);
		code = Texts.trim(code);
		email = Texts.trim(email);
		phone = Texts.trim(phone);
		address = Texts.trim(address);
		note = Texts.trim(note);
	}

}
