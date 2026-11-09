package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

@Converter
class ProductUnitConverter extends ValueEnumConverter<ProductUnit> {

	ProductUnitConverter() {
		super(ProductUnit.class);
	}

}
