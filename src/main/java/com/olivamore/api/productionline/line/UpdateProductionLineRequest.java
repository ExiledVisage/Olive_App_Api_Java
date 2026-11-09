package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code null} gelen alan degismez; {@code description} ise {@code null} ile temizlenebilir. */
record UpdateProductionLineRequest(
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	Patch<String> description,

	@Size(min = 1, message = LaravelMessages.MIN_ITEMS_1)
	List<@Valid LineProductRequest> inputProductionProducts,

	@Size(min = 1, message = LaravelMessages.MIN_ITEMS_1)
	List<@Valid LineProductRequest> outputProductionProducts) {
}
