package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code Http/Requests/StorageMovement}: acilis, giris, cikis, transfer, duzeltme. {@code Rule::requiredIf}
 * kurallari kural metnine cevrilir (duzeltmede {@code is_increase}).
 */
final class StorageMovementRequests {

	private StorageMovementRequests() {
	}

	static Rules opening() {
		return attributeValueRules(Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("production_product_type_id", "required|integer|exists:production_product_types,id")
			.field("quantity", "required|numeric|gt:0")
			.field("unit", "required|string|in:KG,LITRE")
			.field("attribute_values", "nullable|array"))
			.field("note", "nullable|string");
	}

	/** material_id ile var olan materyal, production_product_type_id ile yeni materyal. */
	static Rules inbound() {
		return attributeValueRules(Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("material_id", "required_without:production_product_type_id|nullable|integer|exists:materials,id")
			.field("production_product_type_id",
				"required_without:material_id|nullable|integer|exists:production_product_types,id|prohibits:material_id")
			.field("unit", "nullable|string|in:KG,LITRE|prohibits:material_id")
			.field("attribute_values", "nullable|array|prohibits:material_id"))
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("note", "nullable|string");
	}

	static Rules outbound() {
		return Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("material_id", "required|integer|exists:materials,id")
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("note", "nullable|string");
	}

	static Rules transfer() {
		return Rules.of()
			.field("from_storage_unit_id", "required|integer|exists:storage_units,id")
			.field("to_storage_unit_id", "required|integer|exists:storage_units,id|different:from_storage_unit_id")
			.field("material_id", "required|integer|exists:materials,id")
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("note", "nullable|string");
	}

	/** @param increase {@code $request->boolean('is_increase')} */
	static Rules adjustment(boolean increase) {
		return attributeValueRules(Rules.of()
			.field("storage_unit_id", "required|integer|exists:storage_units,id")
			.field("batch_process_id", "nullable|integer|exists:batch_processes,id")
			.field("quantity", "required|numeric|gt:0")
			.field("is_increase", "required|boolean")
			.field("production_product_type_id", (increase ? "required|" : "") + "nullable|integer|exists:production_product_types,id")
			.field("unit", (increase ? "required|" : "") + "nullable|string|in:KG,LITRE")
			.field("material_id", (increase ? "" : "required|") + "nullable|integer|exists:materials,id")
			.field("attribute_values", "nullable|array"))
			.field("note", "nullable|string");
	}

	private static Rules attributeValueRules(Rules rules) {
		return rules
			.field("attribute_values.*.production_product_attribute_definition_id",
				"required|integer|exists:production_product_attribute_definitions,id")
			.field("attribute_values.*.value_boolean", "nullable|boolean")
			.field("attribute_values.*.value_integer", "nullable|integer")
			.field("attribute_values.*.value_decimal", "nullable|numeric")
			.field("attribute_values.*.value_text", "nullable|string")
			.field("attribute_values.*.value_json", "nullable|array");
	}

}
