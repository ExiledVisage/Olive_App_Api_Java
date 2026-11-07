package com.olivamore.api.analytics.analytics;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code AnalyticsController}'daki {@code $request->validate} kurallari. */
final class AnalyticsRequests {

	private AnalyticsRequests() {
	}

	/** {@code range()}: Y-m-d, to >= from. */
	static Rules range() {
		return Rules.of()
			.field("from", "required|date_format:Y-m-d")
			.field("to", "required|date_format:Y-m-d|after_or_equal:from");
	}

	/** Randiman filtreleri. */
	static Rules yieldFilters() {
		return Rules.of()
			.field("product_type_id", "nullable|integer|exists:production_product_types,id")
			.field("supplier_id", "nullable|integer|exists:suppliers,id");
	}

	/** Tedarikci filtresi. */
	static Rules supplierFilters() {
		return Rules.of()
			.field("product_type_id", "nullable|integer|exists:production_product_types,id");
	}

	/** Rapor olusturucu sorgusu. */
	static Rules query() {
		return Rules.of()
			.field("metric", "required|in:" + String.join(",", ReportSpec.METRICS.keySet()))
			.field("group", "required|in:" + String.join(",", ReportSpec.GROUPS.keySet()))
			.field("chart", "nullable|in:" + String.join(",", ReportSpec.CHARTS.keySet()))
			.field("supplier_id", "nullable|integer")
			.field("supplier_field_id", "nullable|integer")
			.field("product_type_id", "nullable|integer")
			.field("customer_id", "nullable|integer")
			.field("finished_product_id", "nullable|integer");
	}

}
