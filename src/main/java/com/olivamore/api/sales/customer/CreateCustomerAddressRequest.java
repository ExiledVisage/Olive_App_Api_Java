package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateCustomerAddressRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String label,

	@Size(max = 10, message = MessageKeys.SIZE_MAX)
	String country,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String city,

	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String district,

	@Size(max = 150, message = MessageKeys.SIZE_MAX)
	String neighborhood,

	@Size(max = 150, message = MessageKeys.SIZE_MAX)
	String street,

	@Size(max = 20, message = MessageKeys.SIZE_MAX)
	String buildingNo,

	@Size(max = 20, message = MessageKeys.SIZE_MAX)
	String apartmentNo,

	@Size(max = 10, message = MessageKeys.SIZE_MAX)
	String postalCode,

	String fullAddress,

	@DecimalMin(value = "-90", message = MessageKeys.BETWEEN_90)
	@DecimalMax(value = "90", message = MessageKeys.BETWEEN_90)
	BigDecimal latitude,

	@DecimalMin(value = "-180", message = MessageKeys.BETWEEN_180)
	@DecimalMax(value = "180", message = MessageKeys.BETWEEN_180)
	BigDecimal longitude,

	Boolean isDefault) {

	CreateCustomerAddressRequest {
		label = Texts.trim(label);
		country = Texts.trim(country);
		city = Texts.trim(city);
		district = Texts.trim(district);
		neighborhood = Texts.trim(neighborhood);
		street = Texts.trim(street);
		buildingNo = Texts.trim(buildingNo);
		apartmentNo = Texts.trim(apartmentNo);
		postalCode = Texts.trim(postalCode);
		fullAddress = Texts.trim(fullAddress);
	}

}
