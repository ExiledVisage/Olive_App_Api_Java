package com.olivamore.api.supplier;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Tedarikcinin partileri ve kantar tartimlari. */
record SupplierDeliveriesResponse(List<Delivery> data, Summary summary) {

	record Delivery(
		Long productionBatchId,
		String batchCode,
		String status,
		LocalDateTime receivedAt,
		String fieldCode,
		List<String> productTypeNames,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal declaredQuantityKg,
		JsonNode weighedQuantityKg,
		LocalDateTime weighedAt,
		List<WeighInField> weighInFields) {
	}

	record WeighInField(String key, String label, Object value) {
	}

	record Summary(int deliveryCount, BigDecimal totalWeighedKg, LocalDateTime lastReceivedAt) {
	}

}
