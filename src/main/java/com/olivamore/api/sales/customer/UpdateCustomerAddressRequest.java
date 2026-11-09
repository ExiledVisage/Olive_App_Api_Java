package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateCustomerAddressRequest(
	@Size(max = 100, message = LaravelMessages.MAX_100)
	String label,

	@Size(max = 10, message = LaravelMessages.MAX_10)
	String country,

	@Size(max = 100, message = LaravelMessages.MAX_100)
	String city,

	Patch<@Size(max = 100, message = LaravelMessages.MAX_100) String> district,

	Patch<@Size(max = 150, message = LaravelMessages.MAX_150) String> neighborhood,

	Patch<@Size(max = 150, message = LaravelMessages.MAX_150) String> street,

	Patch<@Size(max = 20, message = LaravelMessages.MAX_20) String> buildingNo,

	Patch<@Size(max = 20, message = LaravelMessages.MAX_20) String> apartmentNo,

	Patch<@Size(max = 10, message = LaravelMessages.MAX_10) String> postalCode,

	Patch<String> fullAddress,

	Patch<@DecimalMin(value = "-90", message = LaravelMessages.BETWEEN_90)
		@DecimalMax(value = "90", message = LaravelMessages.BETWEEN_90) BigDecimal> latitude,

	Patch<@DecimalMin(value = "-180", message = LaravelMessages.BETWEEN_180)
		@DecimalMax(value = "180", message = LaravelMessages.BETWEEN_180) BigDecimal> longitude,

	Boolean isDefault) {
}
