package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code currentStock} verilirse devir lotu olarak yazilir. */
record CreatePackagingItemRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotNull(message = MessageKeys.REQUIRED)
	PackagingItemType type,

	@Min(value = 0, message = MessageKeys.MIN)
	Integer currentStock,

	Boolean isActive) {

	CreatePackagingItemRequest {
		name = Texts.trim(name);
	}

}
