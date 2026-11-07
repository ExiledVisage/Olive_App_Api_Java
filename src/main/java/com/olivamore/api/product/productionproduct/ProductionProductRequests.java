package com.olivamore.api.product.productionproduct;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code StoreProductionProductRequest}, {@code UpdateProductionProductRequest}. */
final class ProductionProductRequests {

	private ProductionProductRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("key", "required|string|max:255|unique:production_products,key")
			.field("name", "required|string|max:255|unique:production_products,name")
			.field("description", "nullable|string")
			.field("default_density_kg_per_litre", "nullable|numeric|min:0.0001|max:25");
	}

	static Rules update(long productId) {
		return Rules.of()
			.field("key", "sometimes|string|max:255|unique:production_products,key," + productId)
			.field("name", "sometimes|string|max:255|unique:production_products,name," + productId)
			.field("description", "nullable|string")
			.field("default_density_kg_per_litre", "nullable|numeric|min:0.0001|max:25");
	}

}
