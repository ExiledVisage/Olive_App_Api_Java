package com.olivamore.api.sales.customer;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** {@code addresses} siparis icinde gosterilirken yok. */
public record CustomerResponse(
	Long id,
	String name,
	String companyName,
	String phone,
	String email,
	String notes,
	boolean isActive,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<CustomerAddressResponse> addresses,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public static CustomerResponse withoutAddresses(Customer customer) {
		return of(customer, null);
	}

	static CustomerResponse of(Customer c, List<CustomerAddressResponse> addresses) {
		return new CustomerResponse(c.getId(), c.getName(), c.getCompanyName(), c.getPhone(), c.getEmail(),
			c.getNotes(), c.isActive(), addresses, c.getCreatedAt(), c.getUpdatedAt());
	}

}
