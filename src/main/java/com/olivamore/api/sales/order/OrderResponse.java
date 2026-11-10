package com.olivamore.api.sales.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.sales.customer.CustomerAddressResponse;
import com.olivamore.api.sales.customer.CustomerResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** {@code createdByUser} ve {@code statusHistory} yalnizca detayda. */
record OrderResponse(
	Long id,
	OrderStatus status,
	List<OrderStatus> allowedTransitions,
	LocalDateTime orderDate,
	String note,
	CustomerResponse customer,
	CustomerAddressResponse deliveryAddress,
	List<Item> items,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	UserSummary createdByUser,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<History> statusHistory,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Item(Long id, Product finishedProduct, int quantity) {
	}

	record Product(
		Long id,
		String name,
		BigDecimal volumeLitre,
		Integer totalStock,
		Integer storedStock,
		Integer awaitingShipmentStock,
		Integer unstoredStock,
		boolean isActive,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	}

	record UserSummary(Long id, String name) {
	}

	record History(OrderStatus fromStatus, OrderStatus toStatus, UserSummary changedBy, String note,
		LocalDateTime changedAt) {
	}

}
