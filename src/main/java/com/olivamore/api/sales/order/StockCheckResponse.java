package com.olivamore.api.sales.order;

import java.util.List;

record StockCheckResponse(long orderId, OrderStatus status, boolean isFulfillable, List<Item> items) {

	record Item(
		Long orderItemId,
		Product finishedProduct,
		long requested,
		long totalStock,
		long inStorages,
		long unstored,
		long committedToReadyOrders,
		long available,
		long shortage,
		boolean isSufficient,
		List<Location> locations) {
	}

	record Product(Long id, String name, Double volumeLitre) {
	}

	record Location(Long storageId, String storageName, Long slotId, String slotName, long quantity) {
	}

}
