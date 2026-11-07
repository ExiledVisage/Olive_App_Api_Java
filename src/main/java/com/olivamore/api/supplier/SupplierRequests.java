package com.olivamore.api.supplier;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code StoreSupplierRequest}, {@code UpdateSupplierRequest}, {@code StoreSupplierFieldRequest},
 * {@code UpdateSupplierFieldRequest}.
 */
final class SupplierRequests {

	private SupplierRequests() {
	}

	static Rules store() {
		return supplier("required", "unique:suppliers,code")
			.field("fields", "sometimes|array")
			.field("fields.*.code", "required|string|max:50|distinct|unique:supplier_fields,code")
			.field("fields.*.location", "nullable|string|max:255")
			.field("fields.*.area_decare", "nullable|numeric|min:0")
			.field("fields.*.note", "nullable|string");
	}

	static Rules update(long supplierId) {
		return supplier("sometimes", "unique:suppliers,code," + supplierId);
	}

	static Rules storeField() {
		return field("required", "unique:supplier_fields,code");
	}

	static Rules updateField(long fieldId) {
		return field("sometimes", "unique:supplier_fields,code," + fieldId);
	}

	private static Rules supplier(String presence, String unique) {
		return Rules.of()
			.field("name", presence + "|string|max:255")
			.field("code", presence + "|string|max:50|" + unique)
			.field("phone", "nullable|string|max:50")
			.field("email", "nullable|email|max:255")
			.field("address", "nullable|string")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean");
	}

	private static Rules field(String presence, String unique) {
		return Rules.of()
			.field("code", presence + "|string|max:50|" + unique)
			.field("location", "nullable|string|max:255")
			.field("area_decare", "nullable|numeric|min:0")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean");
	}

}
