package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.validation.Rules;

import java.util.List;

/** Laravel {@code StoreProductionLineRequest}, {@code UpdateProductionLineRequest}. */
final class ProductionLineRequests {

	private ProductionLineRequests() {
	}

	static Rules store() {
		return rules(true);
	}

	static Rules update() {
		return rules(false);
	}

	private static Rules rules(boolean store) {
		Rules rules = Rules.of()
			.field("name", (store ? "required" : "sometimes") + "|string|max:255")
			.field("description", "nullable|string");
		for (String side : List.of("input_production_products", "output_production_products")) {
			rules.field(side, (store ? "required" : "sometimes") + "|array|min:1")
				.field(side + ".*.production_product_id", (store ? "required" : "required_with:" + side)
					+ "|integer|exists:production_products,id|distinct")
				.field(side + ".*.production_product_type_id", "nullable|integer|exists:production_product_types,id")
				.field(side + ".*.sort_order", "nullable|integer|min:1");
		}
		return rules;
	}

}
