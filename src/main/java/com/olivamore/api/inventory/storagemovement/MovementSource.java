package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialOrigin;
import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

public enum MovementSource implements ValueEnum {

	BATCH_PROCESS("BATCH_PROCESS"),
	STORAGE_OPENING("STORAGE_OPENING"),
	INTERNAL_TRANSFER("INTERNAL_TRANSFER"),
	STORAGE_ADJUSTMENT("STORAGE_ADJUSTMENT"),
	MANUAL("MANUAL");

	private final String value;

	MovementSource(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	/** Yeni materyalin koken degeri. */
	MaterialOrigin origin() {
		return MaterialOrigin.valueOf(name());
	}

	@Converter
	static class SourceConverter extends ValueEnumConverter<MovementSource> {

		SourceConverter() {
			super(MovementSource.class);
		}

	}

}
