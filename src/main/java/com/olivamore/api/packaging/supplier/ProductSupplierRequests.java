package com.olivamore.api.packaging.supplier;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/ProductSupplier}: StoreProductSupplierRequest, UpdateProductSupplierRequest. */
final class ProductSupplierRequests {

	private ProductSupplierRequests() {
	}

	static Rules store() {
		return rules("required", "unique:product_suppliers,code");
	}

	static Rules update(long supplierId) {
		return rules("sometimes", "unique:product_suppliers,code," + supplierId);
	}

	private static Rules rules(String presence, String unique) {
		return Rules.of()
			.field("name", presence + "|string|max:255")
			.field("code", presence + "|string|max:50|" + unique)
			.field("phone", "nullable|string|max:50")
			.field("email", "nullable|email|max:255")
			.field("address", "nullable|string")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean");
	}

}
