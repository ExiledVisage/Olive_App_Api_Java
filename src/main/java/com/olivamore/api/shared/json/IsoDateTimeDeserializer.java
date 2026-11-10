package com.olivamore.api.shared.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * ISO-8601 tarih-saat girisi, UTC'ye cevrilir (zaman kolonlari UTC): {@code 2026-11-02T08:00:00+03:00},
 * {@code 2026-11-02T05:00:00Z}, offset'siz {@code 2026-11-02T05:00:00} (UTC kabul edilir) ve yalnizca tarih
 * {@code 2026-11-02} (gun basi).
 */
public class IsoDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

	@Override
	public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
		String text = parser.getValueAsString();
		if (text == null) {
			return null;
		}
		return parse(text).orElseGet(
			() -> (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, text, "Gecersiz tarih"));
	}

	public static Optional<LocalDateTime> parse(String text) {
		try {
			return Optional.of(OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
		}
		catch (DateTimeParseException ignored) {
			// offset'siz bicimler denenir
		}
		try {
			return Optional.of(LocalDateTime.parse(text));
		}
		catch (DateTimeParseException ignored) {
			// yalnizca tarih denenir
		}
		try {
			return Optional.of(LocalDate.parse(text).atStartOfDay());
		}
		catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

}
