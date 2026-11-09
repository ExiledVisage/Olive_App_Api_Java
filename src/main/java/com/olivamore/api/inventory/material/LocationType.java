package com.olivamore.api.inventory.material;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

/** Materyalin bulundugu yer. */
public enum LocationType implements ValueEnum {

	STORAGE_UNIT("storage_unit"),
	PRODUCTION_BATCH("production_batch");

	private final String value;

	LocationType(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	public static class LocationConverter extends ValueEnumConverter<LocationType> {

		public LocationConverter() {
			super(LocationType.class);
		}

	}

}
