package com.olivamore.api.process.processtype;

import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

@Converter
class ProcessCategoryConverter extends ValueEnumConverter<ProcessCategory> {

	ProcessCategoryConverter() {
		super(ProcessCategory.class);
	}

}
