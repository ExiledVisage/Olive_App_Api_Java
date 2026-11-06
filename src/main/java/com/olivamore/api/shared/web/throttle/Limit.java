package com.olivamore.api.shared.web.throttle;

/**
 * Laravel {@code Illuminate\Cache\RateLimiting\Limit}: {@code Limit::perMinute(5)->by('login:' . $email)}.
 */
public record Limit(String key, int maxAttempts, int decaySeconds) {

	public static Limit perMinute(int maxAttempts, String key) {
		return new Limit(key, maxAttempts, 60);
	}

}
