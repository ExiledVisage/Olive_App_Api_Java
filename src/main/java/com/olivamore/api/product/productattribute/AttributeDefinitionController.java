package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.web.ListResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Global attribute tanimlari: yalnizca liste. */
@RestController
class AttributeDefinitionController {

	private final ProductAttributeService service;

	AttributeDefinitionController(ProductAttributeService service) {
		this.service = service;
	}

	@GetMapping("/api/attribute-definitions")
	@PreAuthorize("hasAuthority('attribute_definition.view')")
	ListResponse<AttributeDefinitionResponse> index() {
		return new ListResponse.Plain<>(service.definitions());
	}

}
