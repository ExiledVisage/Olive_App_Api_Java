package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateCustomerRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@Size(max = 255, message = LaravelMessages.MAX_255)
	String companyName,

	@Size(max = 30, message = LaravelMessages.MAX_30)
	String phone,

	@Email(message = LaravelMessages.EMAIL)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String email,

	String notes,

	Boolean isActive,

	List<@Valid CreateCustomerAddressRequest> addresses) {
}
