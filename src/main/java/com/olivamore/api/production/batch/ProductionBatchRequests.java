package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/ProductionBatch} ve controller'daki liste/takvim kurallari. */
final class ProductionBatchRequests {

	private static final String STATUSES = "in:pending,in_progress,completed,cancelled";

	private ProductionBatchRequests() {
	}

	/** Liste: varis gunu araligi. */
	static Rules index() {
		return Rules.of()
			.field("received_from", "nullable|date_format:Y-m-d")
			.field("received_to", "nullable|date_format:Y-m-d");
	}

	static Rules calendar() {
		return Rules.of()
			.field("from", "required|date_format:Y-m-d")
			.field("to", "required|date_format:Y-m-d|after_or_equal:from");
	}

	static Rules store() {
		return Rules.of()
			.field("batch_code", "required|string|max:255|unique:production_batches,batch_code")
			.field("input_production_product_type_ids", "required|array|min:1")
			.field("input_production_product_type_ids.*", "required|integer|distinct|exists:production_product_types,id")
			.field("production_line_id", "required|integer|exists:production_lines,id")
			.field("status", "nullable|string|" + STATUSES)
			.field("quantity_kg", "nullable|numeric|min:0")
			.field("supplier_id", "nullable|integer|exists:suppliers,id")
			.field("supplier_field_id", "nullable|integer|exists:supplier_fields,id")
			.field("received_at", "nullable|date")
			.field("started_at", "nullable|date")
			.field("completed_at", "nullable|date")
			.field("note", "nullable|string");
	}

	static Rules update(long batchId) {
		return Rules.of()
			.field("batch_code", "sometimes|string|max:255|unique:production_batches,batch_code," + batchId)
			.field("status", "sometimes|string|" + STATUSES)
			.field("quantity_kg", "nullable|numeric|min:0")
			.field("supplier_id", "nullable|integer|exists:suppliers,id")
			.field("supplier_field_id", "nullable|integer|exists:supplier_fields,id")
			.field("received_at", "nullable|date")
			.field("started_at", "nullable|date")
			.field("completed_at", "nullable|date")
			.field("note", "nullable|string");
	}

}
