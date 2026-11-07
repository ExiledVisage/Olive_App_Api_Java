package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/StorageUnit}: tank, izinli urun/tip senkronu, attribute kurallari. */
final class StorageUnitRequests {

	private StorageUnitRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("name", "required|string|max:255")
			.field("code", "required|string|max:255|unique:storage_units,code")
			.field("capacity", "required|numeric|gt:0")
			.field("capacity_unit", "required|string|in:KG,LITRE")
			.field("is_blend_tank", "nullable|boolean")
			.field("allow_multiple_production_product_types", "nullable|boolean")
			.field("allow_multiple_production_products", "nullable|boolean")
			.field("description", "nullable|string")
			.field("is_active", "nullable|boolean");
	}

	static Rules update(long unitId) {
		return Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("code", "sometimes|string|max:255|unique:storage_units,code," + unitId)
			.field("capacity", "sometimes|numeric|gt:0")
			.field("capacity_unit", "sometimes|string|in:KG,LITRE")
			.field("is_blend_tank", "nullable|boolean")
			.field("allow_multiple_production_product_types", "nullable|boolean")
			.field("allow_multiple_production_products", "nullable|boolean")
			.field("description", "nullable|string")
			.field("is_active", "sometimes|boolean");
	}

	static Rules syncAllowedProducts() {
		return Rules.of()
			.field("production_product_ids", "required|array")
			.field("production_product_ids.*", "integer|exists:production_products,id");
	}

	static Rules syncAllowedTypes() {
		return Rules.of()
			.field("production_product_type_ids", "required|array")
			.field("production_product_type_ids.*", "integer|exists:production_product_types,id");
	}

	static Rules syncAttributeRules() {
		return Rules.of()
			.field("rules", "required|array")
			.field("rules.*.production_product_attribute_definition_id",
				"required|integer|exists:production_product_attribute_definitions,id")
			.field("rules.*.min_value_decimal", "nullable|numeric")
			.field("rules.*.max_value_decimal", "nullable|numeric")
			.field("rules.*.apply_mode", "nullable|string|in:incoming_only,tank_average_only,incoming_and_average")
			.field("rules.*.is_active", "nullable|boolean")
			.field("rules.*.note", "nullable|string");
	}

}
