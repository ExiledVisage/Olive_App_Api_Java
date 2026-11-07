package com.olivamore.api.productionline.stage;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code StoreProductionStageRequest}, {@code UpdateProductionStageRequest}. */
final class ProductionStageRequests {

	private ProductionStageRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("process_type_id", "nullable|integer|exists:process_types,id")
			.field("name", "required|string|max:255")
			.field("stage_order", "required|integer|min:1")
			.field("is_required", "nullable|boolean")
			.field("requires_approval", "nullable|boolean");
	}

	static Rules update() {
		return Rules.of()
			.field("process_type_id", "sometimes|nullable|integer|exists:process_types,id")
			.field("name", "sometimes|string|max:255")
			.field("stage_order", "sometimes|integer|min:1")
			.field("is_required", "sometimes|boolean")
			.field("requires_approval", "sometimes|boolean");
	}

}
