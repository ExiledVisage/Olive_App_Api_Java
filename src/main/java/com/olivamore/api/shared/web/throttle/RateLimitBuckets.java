package com.olivamore.api.shared.web.throttle;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;

import java.time.Duration;

/**
 * Anahtar basina Bucket4j kovasi. Kova ilk istekle olusur, pencere sonunda tamamen dolar (sabit pencere). Giris
 * yapmamis istemci rastgele anahtar uretebildigi icin bellek sinirli: bosta kalan kovalar silinir, sayi sinirlidir.
 * <p>
 * Tek uygulama ornegi icin. Birden fazla ornek calisacaksa kovalar ortak bir depoya tasinmali.
 */
public class RateLimitBuckets {

	private static final int MAX_BUCKETS = 200_000;
	private static final Duration IDLE_EXPIRY = Duration.ofMinutes(10);

	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
		.maximumSize(MAX_BUCKETS)
		.expireAfterAccess(IDLE_EXPIRY)
		.build();

	Bucket bucket(Limit limit) {
		return buckets.get(limit.key(), key -> Bucket.builder()
			.addLimit(bandwidth -> bandwidth.capacity(limit.maxAttempts())
				.refillIntervally(limit.maxAttempts(), Duration.ofSeconds(limit.decaySeconds())))
			.build());
	}

	/** Testler icin. */
	public void clear() {
		buckets.invalidateAll();
	}

}
