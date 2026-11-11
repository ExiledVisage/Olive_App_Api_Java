package com.olivamore.api.analytics.analytics;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.olivamore.api.analytics.analytics.ReportSpec.Group;
import com.olivamore.api.analytics.analytics.ReportSpec.Metric;

import java.time.LocalDateTime;
import java.util.List;

/** Analiz ekraninin ve rapor olusturucunun cevaplari. */
interface AnalyticsResponse {

	/** Parti toplamlari ve toplamdan hesaplanan oranlar. */
	record BatchTotals(int batchCount, double incomingKg, double pressedKg, double oilKg, double pomaceKg,
		double residueKg, Double yieldPct, Double grossYieldPct, Double avgAcidity) {
	}

	record OrderTotals(long bottleCount, double litres) {
	}

	record Yield(String bucket, BatchTotals summary, List<YieldSlot> series, List<Variety> byVariety) {
	}

	record YieldSlot(String key, String start, @JsonUnwrapped BatchTotals totals) {
	}

	record Variety(Long productTypeId, String name, @JsonUnwrapped BatchTotals totals) {
	}

	record Suppliers(SupplierSummary summary, List<SupplierRow> suppliers) {
	}

	record SupplierSummary(@JsonUnwrapped BatchTotals totals, int supplierCount) {
	}

	record SupplierRow(Long supplierId, String name, String code, LocalDateTime lastDeliveryAt,
		@JsonUnwrapped BatchTotals totals) {
	}

	record Customers(CustomerSummary summary, List<CustomerRow> customers) {
	}

	record CustomerSummary(@JsonUnwrapped OrderTotals totals, int orderCount, int customerCount) {
	}

	record CustomerRow(Long customerId, String name, int orderCount, @JsonUnwrapped OrderTotals totals,
		String topProduct) {
	}

	record CustomerDetail(CustomerRef customer, String bucket, Period summary, Period previous,
		List<CustomerSlot> series, List<ProductRow> products) {
	}

	record CustomerRef(long id, String name) {
	}

	record Period(@JsonUnwrapped OrderTotals totals, int orderCount) {
	}

	record CustomerSlot(String key, String start, @JsonUnwrapped OrderTotals totals, int orderCount) {
	}

	record ProductRow(Long finishedProductId, String name, @JsonUnwrapped OrderTotals totals) {
	}

	record Report(Metric metric, Group group, String bucket, List<ReportRow> rows, String countUnit, Double total) {
	}

	record ReportRow(String key, String label, Double value, int count) {
	}

}
