package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code StoreProductionProductTypeRequest}, {@code UpdateProductionProductTypeRequest}. */
final class ProductionProductTypeRequests {

	private ProductionProductTypeRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("name", "required|string|max:255")
			.field("code", "required|string|max:255|unique:production_product_types,code")
			.field("type", "required|string|max:255")
			.field("unit", "required|string|max:255")
			.field("production_product_id", "required|exists:production_products,id")
			.field("density_kg_per_litre", "nullable|numeric|min:0.0001|max:25");
	}

	static Rules update(long typeId) {
		return Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("code", "sometimes|string|max:255|unique:production_product_types,code," + typeId)
			.field("type", "sometimes|string|max:255")
			.field("unit", "sometimes|string|max:255")
			.field("production_product_id", "sometimes|exists:production_products,id")
			.field("density_kg_per_litre", "nullable|numeric|min:0.0001|max:25");
	}

}
