package com.olivamore.api.packaging.supplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.packaging.lot.PackagingLotResponse;

import java.time.LocalDateTime;
import java.util.List;

/** {@code lots} yalnizca detayda. */
record ProductSupplierResponse(
	Long id,
	String name,
	String code,
	String phone,
	String email,
	String address,
	String note,
	boolean isActive,
	long lotCount,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<PackagingLotResponse> lots,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	static ProductSupplierResponse of(ProductSupplier s, long lotCount, List<PackagingLotResponse> lots) {
		return new ProductSupplierResponse(s.getId(), s.getName(), s.getCode(), s.getPhone(), s.getEmail(),
			s.getAddress(), s.getNote(), s.isActive(), lotCount, lots, s.getCreatedAt(), s.getUpdatedAt());
	}

}
