package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code Http/Requests/FinishedProductStorage}: depo, raf, depoya yerlestirme (StockInRequest), transfer,
 * cikis.
 */
final class FinishedProductStorageRequests {

	private FinishedProductStorageRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("name", "required|string|max:255")
			.field("description", "nullable|string")
			.field("is_active", "boolean");
	}

	static Rules update() {
		return Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("description", "sometimes|nullable|string")
			.field("is_active", "sometimes|boolean");
	}

	static Rules storeSlot() {
		return Rules.of()
			.field("name", "nullable|string|max:50")
			.field("volume_litre", "required|numeric|min:0.001")
			.field("max_quantity", "required|integer|min:1")
			.field("finished_product_id", "nullable|integer|exists:finished_products,id");
	}

	static Rules updateSlot() {
		return Rules.of()
			.field("name", "sometimes|nullable|string|max:50")
			.field("volume_litre", "sometimes|numeric|min:0.001")
			.field("max_quantity", "sometimes|integer|min:1")
			.field("finished_product_id", "sometimes|nullable|integer|exists:finished_products,id");
	}

	/** Tek raf ({@code finished_product_storage_slot_id} + {@code quantity}) ya da {@code placements}. */
	static Rules stockIn() {
		return Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("finished_product_run_id", "nullable|integer|exists:finished_product_runs,id")
			.field("finished_product_storage_slot_id", "required_without:placements|integer|exists:finished_product_storage_slots,id")
			.field("quantity", "required_without:placements|integer|min:1")
			.field("placements", "required_without:finished_product_storage_slot_id|array|min:1")
			.field("placements.*.finished_product_storage_slot_id", "required_with:placements|integer|exists:finished_product_storage_slots,id")
			.field("placements.*.quantity", "required_with:placements|integer|min:1")
			.field("note", "nullable|string|max:1000")
			.message("placements.min", "En az bir rafa miktar girilmelidir.")
			.message("placements.*.quantity.min", "Raf miktarı en az 1 olmalıdır.");
	}

	static Rules transfer() {
		return Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("source_slot_id", "required|integer|exists:finished_product_storage_slots,id")
			.field("target_slot_id", "required|integer|exists:finished_product_storage_slots,id|different:source_slot_id")
			.field("quantity", "required|integer|min:1");
	}

	static Rules stockOut() {
		return Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("finished_product_storage_slot_id", "required|integer|exists:finished_product_storage_slots,id")
			.field("quantity", "required|integer|min:1");
	}

}
