package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code SyncProductionStageExtensionsRequest}. */
final class ProductionStageExtensionRequests {

	private ProductionStageExtensionRequests() {
	}

	static Rules sync() {
		return Rules.of()
			.field("extensions", "required|array")
			.field("extensions.*.stage_extension_type_id", "required|integer|exists:stage_extension_types,id|distinct")
			.field("extensions.*.is_required", "nullable|boolean")
			.field("extensions.*.is_enabled", "nullable|boolean")
			.field("extensions.*.sort_order", "nullable|integer|min:1")
			.field("extensions.*.values", "nullable|array")
			.field("extensions.*.values.*.stage_extension_type_field_id", "nullable|integer|exists:stage_extension_type_fields,id")
			.field("extensions.*.values.*.value_boolean", "nullable|boolean")
			.field("extensions.*.values.*.field_key", "nullable|string")
			.field("extensions.*.values.*.value_integer", "nullable|integer")
			.field("extensions.*.values.*.value_decimal", "nullable|numeric")
			.field("extensions.*.values.*.value_text", "nullable|string")
			.field("extensions.*.values.*.value_json", "nullable|array")
			.field("extensions.*.input_production_product_ids", "nullable|array")
			.field("extensions.*.input_production_product_ids.*", "integer|exists:production_products,id|distinct")
			.field("extensions.*.output_production_product_ids", "nullable|array")
			.field("extensions.*.output_production_product_ids.*", "integer|exists:production_products,id|distinct");
	}

}
