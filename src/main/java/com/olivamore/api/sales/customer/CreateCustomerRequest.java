package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateCustomerRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String companyName,

	@Size(max = 30, message = MessageKeys.SIZE_MAX)
	String phone,

	@Email(message = MessageKeys.EMAIL)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String email,

	String notes,

	Boolean isActive,

	List<@Valid CreateCustomerAddressRequest> addresses) {

	CreateCustomerRequest {
		name = Texts.trim(name);
		companyName = Texts.trim(companyName);
		email = Texts.trim(email);
		phone = Texts.trim(phone);
		notes = Texts.trim(notes);
	}

}
