package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/FinishedProduct}: StoreFinishedProductRequest, UpdateFinishedProductRequest. */
final class FinishedProductRequests {

	private FinishedProductRequests() {
	}

	static Rules store() {
		return rules("required", "required");
	}

	static Rules update() {
		return rules("sometimes", "required_with:components");
	}

	/** Laravel kurallari; {@code distinct} Java'da eklendi: ayni kalem iki kez gelince unique kisiti 500 veriyordu (DEVIATIONS). */
	private static Rules rules(String presence, String itemPresence) {
		return Rules.of()
			.field("name", presence + "|string|max:255")
			.field("finished_product_recipe_id", presence + "|integer|exists:finished_product_recipes,id")
			.field("volume_litre", presence + "|numeric|min:0.001")
			.field("is_active", (presence.equals("required") ? "nullable" : "sometimes") + "|boolean")
			.field("components", presence + "|array|min:1")
			.field("components.*.packaging_item_id", itemPresence + "|integer|distinct|exists:packaging_items,id")
			.field("components.*.quantity", itemPresence + "|integer|min:1");
	}

}
