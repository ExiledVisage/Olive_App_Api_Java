package com.olivamore.api.shared.persistence;

import com.fasterxml.jackson.annotation.JsonValue;

/** JSON'da ve veritabaninda {@link #value()} ile temsil edilen enum. */
public interface ValueEnum {

	@JsonValue
	String value();

}
