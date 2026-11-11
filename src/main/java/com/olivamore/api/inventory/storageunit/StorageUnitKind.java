package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

/** Tank turu; yalnizca tank bosken degisir. */
public enum StorageUnitKind implements ValueEnum {

	FINAL("final"),
	PRE_SETTLING("pre_settling");

	private final String value;

	StorageUnitKind(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	static class KindConverter extends ValueEnumConverter<StorageUnitKind> {

		KindConverter() {
			super(StorageUnitKind.class);
		}

	}

}
