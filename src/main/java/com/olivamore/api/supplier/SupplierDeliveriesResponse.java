package com.olivamore.api.supplier;

import com.fasterxml.jackson.annotation.JsonInclude;
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
		BigDecimal declaredQuantityKg,
		String truckPlate,
		String weighingSlipNo,
		JsonNode weighedQuantityKg,
		LocalDateTime weighedAt,
		List<WeighInField> weighInFields,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		SupplierDeliverySource.Purchase purchase) {
	}

	record WeighInField(String key, String label, Object value) {
	}

	/** @param purchase fiyat yetkisiyle: alim toplamlari; yetki yoksa null */
	record Summary(int deliveryCount, BigDecimal totalWeighedKg, LocalDateTime lastReceivedAt,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		PurchaseSummary purchase) {
	}

	/**
	 * Donemin alim toplamlari: fiyatli teslimatlarin kilosu ve tutari (nakliye dahil), ortalama kg maliyeti
	 * (tutar / fiyatli kilo), KDV haric ve dahil.
	 */
	record PurchaseSummary(int pricedCount, BigDecimal pricedKg, BigDecimal totalExVat, BigDecimal totalWithVat,
		BigDecimal averageCostPerKgExVat, BigDecimal averageCostPerKgWithVat) {
	}

}
