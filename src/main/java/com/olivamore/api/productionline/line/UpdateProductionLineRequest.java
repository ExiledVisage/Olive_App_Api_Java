package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code null} gelen alan degismez; {@code description} ise {@code null} ile temizlenebilir. */
record UpdateProductionLineRequest(
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	Patch<String> description,

	@Size(min = 1, message = MessageKeys.ITEMS_MIN)
	List<@Valid LineProductRequest> inputProductionProducts,

	@Size(min = 1, message = MessageKeys.ITEMS_MIN)
	List<@Valid LineProductRequest> outputProductionProducts) {

	UpdateProductionLineRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
