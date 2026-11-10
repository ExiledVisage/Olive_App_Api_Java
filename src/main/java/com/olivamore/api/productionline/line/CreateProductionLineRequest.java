package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateProductionLineRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	String description,

	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid LineProductRequest> inputProductionProducts,

	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid LineProductRequest> outputProductionProducts) {

	CreateProductionLineRequest {
		name = Texts.trim(name);
		description = Texts.trim(description);
	}

}
