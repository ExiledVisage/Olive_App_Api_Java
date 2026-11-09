package com.olivamore.api.sales.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerAddressResponse(
	Long id,
	Long customerId,
	String label,
	String country,
	String city,
	String district,
	String neighborhood,
	String street,
	String buildingNo,
	String apartmentNo,
	String postalCode,
	String fullAddress,
	Double latitude,
	Double longitude,
	boolean isDefault,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public static CustomerAddressResponse of(CustomerAddress a) {
		return new CustomerAddressResponse(a.getId(), a.getCustomerId(), a.getLabel(), a.getCountry(), a.getCity(),
			a.getDistrict(), a.getNeighborhood(), a.getStreet(), a.getBuildingNo(), a.getApartmentNo(),
			a.getPostalCode(), a.getFullAddress(), number(a.getLatitude()), number(a.getLongitude()),
			a.isDefaultAddress(), a.getCreatedAt(), a.getUpdatedAt());
	}

	private static Double number(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

}
