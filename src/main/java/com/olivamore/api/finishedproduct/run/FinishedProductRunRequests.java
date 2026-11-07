package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code Http/Requests/FinishedProductRun}: StoreFinishedProductRunRequest, UpdateFinishedProductRunStatusRequest,
 * UpdateAttributeValueRequest, SyncRunPackagingsRequest.
 */
final class FinishedProductRunRequests {

	private FinishedProductRunRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("quantity", "required|integer|min:1")
			.field("note", "nullable|string")
			.field("source", "required|array")
			.field("source.storage_unit_id", "required|integer|exists:storage_units,id")
			.field("source.quantity", "nullable|numeric|min:0.001")
			.field("source.loss_percent", "nullable|numeric|min:0|max:2")
			.message("source.loss_percent.max", "Fire payı en fazla %2 olabilir.");
	}

	static Rules updateStatus() {
		return Rules.of().field("status", "required|in:planned,in_progress,completed,cancelled");
	}

	static Rules updateAttributeValue() {
		return Rules.of().field("actual_value", "required|numeric");
	}

	static Rules syncPackagings() {
		return Rules.of()
			.field("packagings", "required|array|min:1")
			.field("packagings.*.packaging_lot_id", "required|integer|distinct|exists:packaging_lots,id")
			.field("packagings.*.quantity", "required|integer|min:1");
	}

}
