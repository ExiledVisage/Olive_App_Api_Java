package com.olivamore.api.process.processtypefield;

import com.olivamore.api.process.FieldDataType;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

@Converter
class FieldDataTypeConverter extends ValueEnumConverter<FieldDataType> {

	FieldDataTypeConverter() {
		super(FieldDataType.class);
	}

}
