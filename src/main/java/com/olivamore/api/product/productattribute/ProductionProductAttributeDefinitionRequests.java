package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code StoreProductionProductAttributeDefinitionRequest}, {@code UpdateProductionProductAttributeDefinitionRequest}.
 * Laravel'deki 500'e yol acan unique kurallari yerine anahtar kontrolu serviste (assertKeyNotMapped).
 */
final class ProductionProductAttributeDefinitionRequests {

	static final String ATTRIBUTE_TYPES = "in:boolean,integer,decimal,text,json";

	private ProductionProductAttributeDefinitionRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("attribute_key", "required|string|max:100")
			.field("label", "required|string|max:255")
			.field("description", "nullable|string")
			.field("data_type", "required|string|" + ATTRIBUTE_TYPES)
			.field("unit", "nullable|string|max:50")
			.field("is_required", "nullable|boolean")
			.field("sort_order", "nullable|integer|min:0");
	}

	static Rules update() {
		return Rules.of()
			.field("attribute_key", "sometimes|string|max:100")
			.field("label", "sometimes|string|max:255")
			.field("description", "nullable|string")
			.field("data_type", "sometimes|string|" + ATTRIBUTE_TYPES)
			.field("unit", "nullable|string|max:50")
			.field("is_required", "nullable|boolean")
			.field("sort_order", "sometimes|integer|min:0");
	}

}
