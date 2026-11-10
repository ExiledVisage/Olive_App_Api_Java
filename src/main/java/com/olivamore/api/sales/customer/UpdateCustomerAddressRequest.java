package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code null} gelen alan degismez; {@code Patch} alanlar {@code null} ile temizlenebilir. */
record UpdateCustomerAddressRequest(
	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String label,

	@Size(max = 10, message = MessageKeys.SIZE_MAX)
	String country,

	@Size(max = 100, message = MessageKeys.SIZE_MAX)
	String city,

	Patch<@Size(max = 100, message = MessageKeys.SIZE_MAX) String> district,

	Patch<@Size(max = 150, message = MessageKeys.SIZE_MAX) String> neighborhood,

	Patch<@Size(max = 150, message = MessageKeys.SIZE_MAX) String> street,

	Patch<@Size(max = 20, message = MessageKeys.SIZE_MAX) String> buildingNo,

	Patch<@Size(max = 20, message = MessageKeys.SIZE_MAX) String> apartmentNo,

	Patch<@Size(max = 10, message = MessageKeys.SIZE_MAX) String> postalCode,

	Patch<String> fullAddress,

	Patch<@DecimalMin(value = "-90", message = MessageKeys.BETWEEN_90)
		@DecimalMax(value = "90", message = MessageKeys.BETWEEN_90) BigDecimal> latitude,

	Patch<@DecimalMin(value = "-180", message = MessageKeys.BETWEEN_180)
		@DecimalMax(value = "180", message = MessageKeys.BETWEEN_180) BigDecimal> longitude,

	Boolean isDefault) {

	UpdateCustomerAddressRequest {
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
