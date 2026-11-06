package com.olivamore.api.shared.web.throttle;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Laravel'in {@code RateLimiter::for('login', fn (Request $request) => [...])} tanimi.
 * Bean olarak tanimlanir, endpoint'e {@code @Throttle("login")} ile baglanir.
 */
public interface NamedLimiter {

	String name();

	List<Limit> limits(HttpServletRequest request);

}
