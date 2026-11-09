package com.olivamore.api.supplier;

import java.time.LocalDateTime;
import java.util.List;

record SupplierResponse(
	Long id,
	String name,
	String code,
	String phone,
	String email,
	String address,
	String note,
	boolean isActive,
	long deliveryCount,
	List<SupplierFieldResponse> fields,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	static SupplierResponse of(Supplier supplier, long deliveryCount) {
		return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getCode(), supplier.getPhone(),
			supplier.getEmail(), supplier.getAddress(), supplier.getNote(), supplier.isActive(), deliveryCount,
			supplier.getFields().stream().map(SupplierFieldResponse::of).toList(), supplier.getCreatedAt(),
			supplier.getUpdatedAt());
	}

}
