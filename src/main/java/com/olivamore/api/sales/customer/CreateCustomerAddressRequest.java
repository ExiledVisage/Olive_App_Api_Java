package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateCustomerAddressRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 100, message = LaravelMessages.MAX_100)
	String label,

	@Size(max = 10, message = LaravelMessages.MAX_10)
	String country,

	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 100, message = LaravelMessages.MAX_100)
	String city,

	@Size(max = 100, message = LaravelMessages.MAX_100)
	String district,

	@Size(max = 150, message = LaravelMessages.MAX_150)
	String neighborhood,

	@Size(max = 150, message = LaravelMessages.MAX_150)
	String street,

	@Size(max = 20, message = LaravelMessages.MAX_20)
	String buildingNo,

	@Size(max = 20, message = LaravelMessages.MAX_20)
	String apartmentNo,

	@Size(max = 10, message = LaravelMessages.MAX_10)
	String postalCode,

	String fullAddress,

	@DecimalMin(value = "-90", message = LaravelMessages.BETWEEN_90)
	@DecimalMax(value = "90", message = LaravelMessages.BETWEEN_90)
	BigDecimal latitude,

	@DecimalMin(value = "-180", message = LaravelMessages.BETWEEN_180)
	@DecimalMax(value = "180", message = LaravelMessages.BETWEEN_180)
	BigDecimal longitude,

	Boolean isDefault) {
}
