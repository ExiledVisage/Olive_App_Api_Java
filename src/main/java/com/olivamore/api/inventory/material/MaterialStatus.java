package com.olivamore.api.inventory.material;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

public enum MaterialStatus implements ValueEnum {

	ACTIVE("active"),
	CONSUMED("consumed"),
	DISCARDED("discarded");

	private final String value;

	MaterialStatus(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	public static class StatusConverter extends ValueEnumConverter<MaterialStatus> {

		public StatusConverter() {
			super(MaterialStatus.class);
		}

	}

}
