package com.olivamore.api.shared.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Zaman kolonlari saat dilimi olmadan UTC tutulur ({@code timestamp without time zone}, bkz.
 * {@link com.olivamore.api.shared.time.Timestamps}). JSON'a ISO-8601 olarak, UTC offset'iyle yazilir:
 * <pre>"2026-11-06T15:35:14Z"</pre>
 */
public class UtcDateTimeSerializer extends ValueSerializer<LocalDateTime> {

	@Override
	public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
		gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atOffset(ZoneOffset.UTC)));
	}

}
