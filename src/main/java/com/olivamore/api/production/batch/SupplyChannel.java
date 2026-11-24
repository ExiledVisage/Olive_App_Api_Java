package com.olivamore.api.production.batch;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

/** Zeytinin geldigi kanal: ciftci, tuccar ya da kendi bahcemiz. */
public enum SupplyChannel implements ValueEnum {

	FARMER("farmer"),
	TRADER("trader"),
	OWN_GROVE("own_grove");

	private final String value;

	SupplyChannel(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	static class ChannelConverter extends ValueEnumConverter<SupplyChannel> {

		ChannelConverter() {
			super(SupplyChannel.class);
		}

	}

}
