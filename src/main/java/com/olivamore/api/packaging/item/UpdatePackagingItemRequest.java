package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Size;

/** {@code null} gelen alan degismez. Eldeki adet lot hareketleriyle degisir. */
record UpdatePackagingItemRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	PackagingItemType type,

	Boolean isActive) {

	UpdatePackagingItemRequest {
		name = Texts.trim(name);
	}

}
