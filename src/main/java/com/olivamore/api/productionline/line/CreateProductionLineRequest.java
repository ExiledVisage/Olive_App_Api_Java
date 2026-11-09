package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

record CreateProductionLineRequest(
	@NotBlank(message = LaravelMessages.REQUIRED)
	@Size(max = 255, message = LaravelMessages.MAX_255)
	String name,

	String description,

	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid LineProductRequest> inputProductionProducts,

	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid LineProductRequest> outputProductionProducts) {
}
