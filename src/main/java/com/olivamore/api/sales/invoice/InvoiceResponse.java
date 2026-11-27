package com.olivamore.api.sales.invoice;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** {@code lines} ve {@code vatBreakdown} yalnizca detayda. Tutarlar TL, kurus hassasiyetinde. */
record InvoiceResponse(
	Long id,
	String number,
	/** issued, cancelled */
	String status,
	LocalDate issueDate,
	Order order,
	Long customerId,
	Buyer buyer,
	String currency,
	BigDecimal netTotal,
	BigDecimal vatTotal,
	BigDecimal grossTotal,
	String note,
	User createdBy,
	LocalDateTime cancelledAt,
	User cancelledBy,
	String cancelReason,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<VatGroup> vatBreakdown,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Line> lines,
	LocalDateTime createdAt) {

	/** @param source manual, woocommerce, trendyol, hepsiburada */
	record Order(Long id, String source, String externalRef) {
	}

	record Buyer(String name, String taxNumber, String taxOffice, String address, String district, String city,
		String country, String email, String phone) {
	}

	record User(Long id, String name) {
	}

	record VatGroup(int rate, BigDecimal net, BigDecimal vat) {
	}

	/** @param unitPrice KDV dahil */
	record Line(Long id, Long finishedProductId, String description, int quantity, BigDecimal unitPrice, int vatRate,
		BigDecimal netAmount, BigDecimal vatAmount, BigDecimal grossAmount) {
	}

}
