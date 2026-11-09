package com.olivamore.api.product.productattribute;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.ListResponse;
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
	@RequiresPermission("attribute_definition.view")
	ListResponse<AttributeDefinitionResponse> index() {
		return new ListResponse.Plain<>(service.definitions());
	}

}
