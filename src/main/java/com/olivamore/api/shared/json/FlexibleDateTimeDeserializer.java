package com.olivamore.api.shared.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Tarih alanlari: {@code 2026-11-03}, {@code 2026-11-01 09:30}, {@code 2026-11-02 08:00:00} ve ISO-8601.
 * Saat dilimi bilgisi yazilan saati degistirmez.
 */
public class FlexibleDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

	private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
		DateTimeFormatter.ISO_LOCAL_DATE_TIME,
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

	@Override
	public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
		String text = parser.getValueAsString();
		if (text == null || text.isBlank()) {
			return null;
		}
		return parse(text.trim()).orElseGet(
			() -> (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, text, "Gecersiz tarih"));
	}

	public static Optional<LocalDateTime> parse(String text) {
		try {
			return Optional.of(OffsetDateTime.parse(text).toLocalDateTime());
		}
		catch (DateTimeParseException ignored) {
			// yerel bicimler denenir
		}
		for (DateTimeFormatter format : LOCAL_FORMATS) {
			try {
				return Optional.of(LocalDateTime.parse(text, format));
			}
			catch (DateTimeParseException ignored) {
				// siradaki bicim
			}
		}
		try {
			return Optional.of(LocalDate.parse(text).atStartOfDay());
		}
		catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

}
