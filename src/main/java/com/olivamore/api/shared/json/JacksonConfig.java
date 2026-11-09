package com.olivamore.api.shared.json;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * Laravel uyumlu JSON. snake_case alan adlari {@code application.yaml}'da
 * ({@code spring.jackson.property-naming-strategy}). Burada:
 * <ul>
 *   <li>tarih-saat tipleri Laravel formatinda yazilir</li>
 *   <li>gelen metinler Laravel gibi kirpilir, bos metin null olur</li>
 * </ul>
 * {@link java.time.LocalDate} burada ayarlanmaz: Laravel'de tarih kolonlari
 * cast'e gore ya "2026-07-15" ya da "2026-07-15T00:00:00.000000Z" donuyor.
 * Ikinci bicim gerekiyorsa alana {@code @JsonSerialize(using = LaravelDateTimeSerializer.class)} yazilir.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

	@Bean
	JacksonModule laravelModule() {
		LaravelDateTimeSerializer dateTime = new LaravelDateTimeSerializer();
		return new SimpleModule("laravel")
			.addSerializer(Instant.class, dateTime)
			.addSerializer(OffsetDateTime.class, dateTime)
			.addSerializer(ZonedDateTime.class, dateTime)
			.addSerializer(LocalDateTime.class, dateTime)
			.addDeserializer(String.class, new LaravelStringDeserializer())
			.addDeserializer(Boolean.class, new StrictBooleanDeserializer())
			.addDeserializer(Boolean.TYPE, new StrictBooleanDeserializer());
	}

}
