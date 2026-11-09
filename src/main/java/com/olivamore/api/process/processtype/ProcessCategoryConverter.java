package com.olivamore.api.process.processtype;

import com.olivamore.api.process.ProcessCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class ProcessCategoryConverter implements AttributeConverter<ProcessCategory, String> {

	@Override
	public String convertToDatabaseColumn(ProcessCategory category) {
		return category == null ? null : category.value();
	}

	@Override
	public ProcessCategory convertToEntityAttribute(String value) {
		return value == null ? null : ProcessCategory.of(value);
	}

}
