package com.olivamore.api.sales.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.sales.customer.CustomerAddressResponse;
import com.olivamore.api.sales.customer.CustomerResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** {@code createdByUser} ve {@code statusHistory} yalnizca detayda. */
record OrderResponse(
	Long id,
	OrderStatus status,
	List<OrderStatus> allowedTransitions,
	LocalDateTime orderDate,
	String note,
	/** manual, woocommerce, trendyol, hepsiburada */
	String source,
	String externalRef,
	CustomerResponse customer,
	CustomerAddressResponse deliveryAddress,
	List<Item> items,
	/** KDV dahil siparis tutari; fiyati olmayan kalem varsa bos. */
	BigDecimal total,
	/** Gecerli fatura; kesilmediyse bos. */
	Invoice invoice,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	UserSummary createdByUser,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<History> statusHistory,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	/** @param unitPrice KDV dahil; bos olabilir */
	record Item(Long id, Product finishedProduct, int quantity, BigDecimal unitPrice, int vatRate,
		BigDecimal lineTotal) {
	}

	record Invoice(Long id, String number, LocalDate issueDate, BigDecimal grossTotal) {
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
