package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code currentStock} verilirse devir lotu olarak yazilir. */
record CreatePackagingItemRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	@NotNull(message = LaravelMessages.REQUIRED)
	PackagingItemType type,

	@Min(value = 0, message = LaravelMessages.MIN_0)
	Integer currentStock,

	Boolean isActive) {
}
