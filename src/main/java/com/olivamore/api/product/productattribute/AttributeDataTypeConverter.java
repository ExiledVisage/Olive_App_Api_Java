package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

@Converter
class AttributeDataTypeConverter extends ValueEnumConverter<AttributeDataType> {

	AttributeDataTypeConverter() {
		super(AttributeDataType.class);
	}

}
