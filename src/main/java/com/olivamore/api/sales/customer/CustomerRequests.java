package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code Http/Requests/Customer}: StoreCustomerRequest, UpdateCustomerRequest, StoreCustomerAddressRequest,
 * UpdateCustomerAddressRequest, StoreCustomerPreferredRecipeRequest.
 */
final class CustomerRequests {

	private CustomerRequests() {
	}

	/** StoreCustomerRequest: musteri ve (istege bagli) adresleri. */
	static Rules store() {
		Rules rules = Rules.of()
			.field("name", "required|string|max:255")
			.field("company_name", "nullable|string|max:255")
			.field("phone", "nullable|string|max:30")
			.field("email", "nullable|email|max:255")
			.field("notes", "nullable|string")
			.field("is_active", "nullable|boolean")
			.field("addresses", "nullable|array");
		return address(rules, "addresses.*.", "required");
	}

	/** UpdateCustomerRequest */
	static Rules update() {
		return Rules.of()
			.field("name", "sometimes|required|string|max:255")
			.field("company_name", "nullable|string|max:255")
			.field("phone", "nullable|string|max:30")
			.field("email", "nullable|email|max:255")
			.field("notes", "nullable|string")
			.field("is_active", "nullable|boolean");
	}

	/** StoreCustomerAddressRequest */
	static Rules storeAddress() {
		return address(Rules.of(), "", "required");
	}

	/** UpdateCustomerAddressRequest */
	static Rules updateAddress() {
		return address(Rules.of(), "", "sometimes|required");
	}

	/** StoreCustomerPreferredRecipeRequest */
	static Rules storePreferredRecipe() {
		return Rules.of()
			.field("finished_product_recipe_id", "required|integer|exists:finished_product_recipes,id")
			.field("note", "nullable|string|max:255");
	}

	private static Rules address(Rules rules, String prefix, String presence) {
		return rules
			.field(prefix + "label", presence + "|string|max:100")
			.field(prefix + "country", "nullable|string|max:10")
			.field(prefix + "city", presence + "|string|max:100")
			.field(prefix + "district", "nullable|string|max:100")
			.field(prefix + "neighborhood", "nullable|string|max:150")
			.field(prefix + "street", "nullable|string|max:150")
			.field(prefix + "building_no", "nullable|string|max:20")
			.field(prefix + "apartment_no", "nullable|string|max:20")
			.field(prefix + "postal_code", "nullable|string|max:10")
			.field(prefix + "full_address", "nullable|string")
			.field(prefix + "latitude", "nullable|numeric|between:-90,90")
			.field(prefix + "longitude", "nullable|numeric|between:-180,180")
			.field(prefix + "is_default", "nullable|boolean");
	}

}
