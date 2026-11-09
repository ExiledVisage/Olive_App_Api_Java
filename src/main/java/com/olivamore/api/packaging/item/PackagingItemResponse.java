package com.olivamore.api.packaging.item;

import java.time.LocalDateTime;

record PackagingItemResponse(
	Long id,
	String name,
	PackagingItemType type,
	int currentStock,
	long lotCount,
	long activeLotCount,
	boolean isActive,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	static PackagingItemResponse of(PackagingItem item, long lotCount, long activeLotCount) {
		return new PackagingItemResponse(item.getId(), item.getName(), item.getType(), item.getCurrentStock(),
			lotCount, activeLotCount, item.isActive(), item.getCreatedAt(), item.getUpdatedAt());
	}

}
