package com.olivamore.api.shared.web.throttle;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laravel {@code Illuminate\Cache\RateLimiter}'in bellek ici karsiligi: anahtar basina sabit
 * pencere. Pencere ilk denemeyle baslar ve {@code decaySeconds} sonra biter; dolunca sayac sifirlanir.
 * <p>
 * Tek uygulama ornegi icin yeterli. Birden fazla ornek calisacaksa (yatay olcekleme) sayaclar
 * ortak bir depoya (Redis) tasinmali.
 */
public class RateLimiter {

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
	private final Clock clock;

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** {@code tooManyAttempts}: sayac sinira ulasti ve pencere hala acik mi. */
	public boolean tooManyAttempts(String key, int maxAttempts) {
		Window window = windows.get(key);
		if (window == null) {
			return false;
		}
		if (window.expiresAt <= now()) {
			windows.remove(key, window);
			return false;
		}
		return window.hits >= maxAttempts;
	}

	/** {@code hit}: pencere yoksa (ya da bittiyse) acar, sayaci artirir. */
	public int hit(String key, int decaySeconds) {
		long now = now();
		Window window = windows.compute(key, (k, current) -> {
			Window w = current == null || current.expiresAt <= now ? new Window(now + decaySeconds) : current;
			w.hits++;
			return w;
		});
		return window.hits;
	}

	/** {@code remaining}: kalan hak (0'in altina inmez). */
	public int remaining(String key, int maxAttempts) {
		Window window = windows.get(key);
		int hits = window == null || window.expiresAt <= now() ? 0 : window.hits;
		return Math.max(0, maxAttempts - hits);
	}

	/** {@code availableIn}: pencerenin bitmesine kalan saniye. */
	public long availableIn(String key) {
		Window window = windows.get(key);
		return window == null ? 0 : Math.max(0, window.expiresAt - now());
	}

	public long now() {
		return clock.instant().getEpochSecond();
	}

	/** Testler icin. */
	public void clear() {
		windows.clear();
	}

	private static final class Window {

		final long expiresAt;
		int hits;

		Window(long expiresAt) {
			this.expiresAt = expiresAt;
		}

	}

}
