package com.olivamore.api.shared.web.throttle;

/**
 * Bir anahtar icin istek siniri: {@code decaySeconds} penceresinde en fazla {@code maxAttempts}.
 */
public record Limit(String key, int maxAttempts, int decaySeconds) {

	public static Limit perMinute(int maxAttempts, String key) {
		return new Limit(key, maxAttempts, 60);
	}

}
