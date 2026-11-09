package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

/** Kuralin uygulandigi yer. */
public enum ApplyMode implements ValueEnum {

	INCOMING_ONLY("incoming_only"),
	TANK_AVERAGE_ONLY("tank_average_only"),
	INCOMING_AND_AVERAGE("incoming_and_average");

	private final String value;

	ApplyMode(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	static class ModeConverter extends ValueEnumConverter<ApplyMode> {

		ModeConverter() {
			super(ApplyMode.class);
		}

	}

}
