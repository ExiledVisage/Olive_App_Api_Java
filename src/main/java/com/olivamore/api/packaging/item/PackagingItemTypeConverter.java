package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

@Converter
class PackagingItemTypeConverter extends ValueEnumConverter<PackagingItemType> {

	PackagingItemTypeConverter() {
		super(PackagingItemType.class);
	}

}
