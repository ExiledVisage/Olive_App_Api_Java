package com.olivamore.api.inventory.material;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

public enum MaterialOrigin implements ValueEnum {

	BATCH_PROCESS("BATCH_PROCESS"),
	STORAGE_OPENING("STORAGE_OPENING"),
	STORAGE_ADJUSTMENT("STORAGE_ADJUSTMENT"),
	MANUAL("MANUAL"),
	/** Son tankin tip materyali: girisler buna eklenir, partisi yoktur. */
	TANK("TANK");

	private final String value;

	MaterialOrigin(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	public static class OriginConverter extends ValueEnumConverter<MaterialOrigin> {

		public OriginConverter() {
			super(MaterialOrigin.class);
		}

	}

}
