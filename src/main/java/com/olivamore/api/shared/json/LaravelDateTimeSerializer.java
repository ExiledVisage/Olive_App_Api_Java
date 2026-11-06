package com.olivamore.api.shared.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * Laravel'in (Carbon) tarih-saat JSON formati: UTC, mikrosaniye, {@code Z} ile.
 * <pre>"2026-11-06T15:35:14.000000Z"</pre>
 * Laravel'de saat dilimi UTC ({@code config/app.php}). {@link LocalDateTime}
 * degerleri de UTC kabul edilir, cunku Laravel timestamp kolonlarini
 * saat dilimi olmadan UTC olarak yaziyor.
 */
public class LaravelDateTimeSerializer extends ValueSerializer<TemporalAccessor> {

	public static final DateTimeFormatter FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

	@Override
	public void serialize(TemporalAccessor value, JsonGenerator gen, SerializationContext ctxt) {
		gen.writeString(format(value));
	}

	public static String format(TemporalAccessor value) {
		Instant instant = switch (value) {
			case Instant i -> i;
			case OffsetDateTime o -> o.toInstant();
			case ZonedDateTime z -> z.toInstant();
			case LocalDateTime l -> l.toInstant(ZoneOffset.UTC);
			// Laravel'in 'date' cast'i gece yarisi Carbon'u dondurur: 2026-07-15T00:00:00.000000Z
			case LocalDate d -> d.atStartOfDay().toInstant(ZoneOffset.UTC);
			default -> Instant.from(value);
		};
		return FORMAT.format(instant);
	}

}
