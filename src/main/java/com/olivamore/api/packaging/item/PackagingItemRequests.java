package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/PackagingItem}: StorePackagingItemRequest, UpdatePackagingItemRequest. */
final class PackagingItemRequests {

	private PackagingItemRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("name", "required|string|max:255")
			.field("type", "required|in:BOTTLE,CAP,LABEL")
			.field("current_stock", "nullable|integer|min:0")
			.field("is_active", "nullable|boolean");
	}

	/** {@code current_stock} dogrulanir ama yazilmaz. */
	static Rules update() {
		return Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("type", "sometimes|in:BOTTLE,CAP,LABEL")
			.field("current_stock", "sometimes|integer|min:0")
			.field("is_active", "sometimes|boolean");
	}

}
