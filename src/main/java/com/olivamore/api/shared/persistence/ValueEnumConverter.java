package com.olivamore.api.shared.persistence;

import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

/** {@link ValueEnum} ile metin kolon arasinda; her enum icin bir alt sinif yeterli. */
public abstract class ValueEnumConverter<E extends Enum<E> & ValueEnum> implements AttributeConverter<E, String> {

	private final Class<E> type;

	protected ValueEnumConverter(Class<E> type) {
		this.type = type;
	}

	@Override
	public String convertToDatabaseColumn(E attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public E convertToEntityAttribute(String column) {
		if (column == null) {
			return null;
		}
		return Arrays.stream(type.getEnumConstants())
			.filter(constant -> constant.value().equals(column))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(type.getSimpleName() + ": " + column));
	}

}
