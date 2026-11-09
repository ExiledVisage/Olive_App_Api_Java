package com.olivamore.api.supplier;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

record SupplierFieldResponse(
	Long id,
	Long supplierId,
	String code,
	String location,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	BigDecimal areaDecare,
	String note,
	boolean isActive) {

	static SupplierFieldResponse of(SupplierField field) {
		return new SupplierFieldResponse(field.getId(), field.getSupplier().getId(), field.getCode(),
			field.getLocation(), field.getAreaDecare(), field.getNote(), field.isActive());
	}

}
