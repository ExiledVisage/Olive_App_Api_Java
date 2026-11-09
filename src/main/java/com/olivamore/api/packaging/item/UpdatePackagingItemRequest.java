package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez. Eldeki adet lot hareketleriyle degisir. */
record UpdatePackagingItemRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	PackagingItemType type,

	Boolean isActive) {
}
