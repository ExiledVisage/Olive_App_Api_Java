package com.olivamore.api.sales.order;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/Order}: StoreOrderRequest, UpdateOrderRequest, UpdateOrderStatusRequest. */
final class OrderRequests {

	private OrderRequests() {
	}

	/** StoreOrderRequest */
	static Rules store() {
		return Rules.of()
			.field("customer_id", "required|integer|exists:customers,id")
			.field("delivery_address_id", "required|integer|exists:customer_addresses,id")
			.field("order_date", "required|date")
			.field("note", "nullable|string")
			.field("items", "required|array|min:1")
			.field("items.*.finished_product_id", "required|integer|exists:finished_products,id|distinct")
			.field("items.*.quantity", "required|integer|min:1");
	}

	/** UpdateOrderRequest */
	static Rules update() {
		return Rules.of()
			.field("customer_id", "sometimes|integer|exists:customers,id")
			.field("delivery_address_id", "sometimes|integer|exists:customer_addresses,id")
			.field("order_date", "sometimes|date")
			.field("note", "nullable|string")
			.field("items", "sometimes|array|min:1")
			.field("items.*.finished_product_id", "required_with:items|integer|exists:finished_products,id|distinct")
			.field("items.*.quantity", "required_with:items|integer|min:1");
	}

	/** UpdateOrderStatusRequest: {@code picks} hazirlamada raf secimi (verilmezse FIFO). */
	static Rules updateStatus() {
		return Rules.of()
			.field("status", "required|in:confirmed,stock_check,in_production,ready,shipped,cancelled")
			.field("note", "nullable|string|max:1000")
			.field("picks", "sometimes|array")
			.field("picks.*.order_item_id", "required_with:picks|integer|exists:order_items,id")
			.field("picks.*.slot_id", "required_with:picks|integer|exists:finished_product_storage_slots,id")
			.field("picks.*.quantity", "required_with:picks|integer|min:1");
	}

}
