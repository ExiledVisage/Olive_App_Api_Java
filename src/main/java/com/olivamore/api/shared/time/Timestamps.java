package com.olivamore.api.shared.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Laravel'in {@code created_at} / {@code updated_at} degerleri: UTC, saniyeye kirpilmis.
 * <p>
 * Kolonlar {@code timestamp(0) without time zone}. Laravel {@code now()}'u saniyeye kirpip
 * UTC duvar saati olarak yazar. Java'da milisaniyeli bir deger yazilsa PostgreSQL onu
 * <b>yuvarlar</b> (14:00:00.7 -> 14:00:01); bu yuzden her zaman buradan alinir.
 */
public final class Timestamps {

	private static Clock clock = Clock.systemUTC();

	private Timestamps() {
	}

	public static LocalDateTime now() {
		return LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).truncatedTo(ChronoUnit.SECONDS);
	}

	/** Testler icin. */
	public static void useClock(Clock testClock) {
		clock = testClock;
	}

}
