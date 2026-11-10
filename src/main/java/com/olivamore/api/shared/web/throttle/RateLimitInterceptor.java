package com.olivamore.api.shared.web.throttle;

import com.olivamore.api.shared.error.HttpStatusException;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link Throttle} ile isaretli endpoint'ler: limitler sirayla denenir, biri dolduysa 429 ve {@code Retry-After}.
 * Basarili cevapta {@code X-RateLimit-*} kalan hakki en az olan limiti gosterir.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

	/** Kullaniciyi bilen modul (identity) saglar; sabit limitlerin anahtari icin. */
	public interface UserKey extends Function<HttpServletRequest, String> {
	}

	private final RateLimitBuckets buckets;
	private final Map<String, NamedLimiter> named;
	private final UserKey userKey;

	public RateLimitInterceptor(RateLimitBuckets buckets, List<NamedLimiter> named, UserKey userKey) {
		this.buckets = buckets;
		this.named = named.stream().collect(Collectors.toMap(NamedLimiter::name, n -> n));
		this.userKey = userKey;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		Throttle throttle = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), Throttle.class);
		if (throttle == null) {
			throttle = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), Throttle.class);
		}
		if (throttle == null) {
			return true;
		}
		Limit shown = null;
		long lowest = Long.MAX_VALUE;
		for (Limit limit : limits(throttle, request)) {
			ConsumptionProbe probe = buckets.bucket(limit).tryConsumeAndReturnRemaining(1);
			if (!probe.isConsumed()) {
				throw tooMany(limit, probe);
			}
			if (probe.getRemainingTokens() < lowest) {
				lowest = probe.getRemainingTokens();
				shown = limit;
			}
		}
		response.setHeader("X-RateLimit-Limit", String.valueOf(shown.maxAttempts()));
		response.setHeader("X-RateLimit-Remaining", String.valueOf(lowest));
		return true;
	}

	private static HttpStatusException tooMany(Limit limit, ConsumptionProbe probe) {
		long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("X-RateLimit-Limit", String.valueOf(limit.maxAttempts()));
		headers.put("X-RateLimit-Remaining", "0");
		headers.put("Retry-After", String.valueOf(retryAfter));
		headers.put("X-RateLimit-Reset", String.valueOf(Instant.now().getEpochSecond() + retryAfter));
		return new HttpStatusException(429, "{error.too-many-attempts}", headers);
	}

	private List<Limit> limits(Throttle throttle, HttpServletRequest request) {
		if (!throttle.value().isEmpty()) {
			NamedLimiter limiter = named.get(throttle.value());
			if (limiter == null) {
				throw new IllegalStateException("Tanimsiz rate limiter: " + throttle.value());
			}
			return limiter.limits(request);
		}
		// Anahtar endpoint'e bagli degil: istemcinin sabit limitli butun endpoint'leri ayni sayaci paylasir
		String user = userKey.apply(request);
		String key = "throttle:" + (user != null ? "user:" + user : "ip:" + request.getRemoteAddr());
		return List.of(new Limit(key, throttle.maxAttempts(), throttle.decayMinutes() * 60));
	}

}
