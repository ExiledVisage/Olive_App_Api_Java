package com.olivamore.api.supplier;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateSupplierRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 50, message = LaravelMessages.MAX_50)
	String code,

	@Size(max = 50, message = LaravelMessages.MAX_50)
	String phone,

	@Email(message = LaravelMessages.EMAIL)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String email,

	String address,

	String note,

	Boolean isActive,

	List<@Valid CreateSupplierFieldRequest> fields) {
}
