package com.olivamore.api.packaging.supplier;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateProductSupplierRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String code,

	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	String phone,

	@Email(message = MessageKeys.EMAIL)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String email,

	String address,

	String note,

	Boolean isActive) {

	CreateProductSupplierRequest {
		name = Texts.trim(name);
		code = Texts.trim(code);
		email = Texts.trim(email);
		phone = Texts.trim(phone);
		address = Texts.trim(address);
		note = Texts.trim(note);
	}

}
