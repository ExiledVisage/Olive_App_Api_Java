package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

public enum MovementType implements ValueEnum {

	IN("IN"),
	OUT("OUT"),
	TRANSFER_IN("TRANSFER_IN"),
	TRANSFER_OUT("TRANSFER_OUT"),
	OPENING("OPENING"),
	ADJUSTMENT_IN("ADJUSTMENT_IN"),
	ADJUSTMENT_OUT("ADJUSTMENT_OUT");

	private final String value;

	MovementType(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	static class TypeConverter extends ValueEnumConverter<MovementType> {

		TypeConverter() {
			super(MovementType.class);
		}

	}

}
