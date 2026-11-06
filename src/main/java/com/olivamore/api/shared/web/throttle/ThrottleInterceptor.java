package com.olivamore.api.shared.web.throttle;

import com.olivamore.api.shared.error.HttpStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Laravel {@code ThrottleRequests::handleRequest}:
 * <ol>
 *   <li>her limit icin sirayla: sinira ulasildiysa 429, degilse sayac artar</li>
 *   <li>cevaba {@code X-RateLimit-Limit} / {@code X-RateLimit-Remaining}: kalan hakki en az olan limit</li>
 *   <li>429 cevabi: {@code {"message": "Too Many Attempts."}} + {@code Retry-After} ve {@code X-RateLimit-Reset}</li>
 * </ol>
 */
public class ThrottleInterceptor implements HandlerInterceptor {

	/** Kullaniciyi bilen modul (identity) bunu saglar; {@code throttle:30,1} anahtari icin. */
	public interface UserKey extends Function<HttpServletRequest, String> {
	}

	private final RateLimiter limiter;
	private final Map<String, NamedLimiter> named;
	private final UserKey userKey;

	public ThrottleInterceptor(RateLimiter limiter, List<NamedLimiter> named, UserKey userKey) {
		this.limiter = limiter;
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
		List<Limit> limits = limits(throttle, request);

		for (Limit limit : limits) {
			if (limiter.tooManyAttempts(limit.key(), limit.maxAttempts())) {
				long retryAfter = limiter.availableIn(limit.key());
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("X-RateLimit-Limit", String.valueOf(limit.maxAttempts()));
				headers.put("X-RateLimit-Remaining", "0");
				headers.put("Retry-After", String.valueOf(retryAfter));
				headers.put("X-RateLimit-Reset", String.valueOf(limiter.now() + retryAfter));
				throw new HttpStatusException(429, "Too Many Attempts.", headers);
			}
			limiter.hit(limit.key(), limit.decaySeconds());
		}

		// addHeaders: her limit icin; kalan hakki daha az olan oncekini ezer
		Integer shown = null;
		for (Limit limit : limits) {
			int remaining = limiter.remaining(limit.key(), limit.maxAttempts());
			if (shown == null || remaining < shown) {
				response.setHeader("X-RateLimit-Limit", String.valueOf(limit.maxAttempts()));
				response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
				shown = remaining;
			}
		}
		return true;
	}

	private List<Limit> limits(Throttle throttle, HttpServletRequest request) {
		if (!throttle.value().isEmpty()) {
			NamedLimiter limiter = named.get(throttle.value());
			if (limiter == null) {
				throw new IllegalStateException("Tanimsiz rate limiter: " + throttle.value());
			}
			return limiter.limits(request);
		}
		// throttle:N,M -> resolveRequestSignature: kullanici varsa kimligi, yoksa adres
		String user = userKey.apply(request);
		String signature = user != null ? "user:" + user : "ip:" + request.getRemoteAddr();
		String key = "throttle:" + request.getRequestURI() + ":" + signature;
		return List.of(new Limit(key, throttle.maxAttempts(), throttle.decayMinutes() * 60));
	}

}
