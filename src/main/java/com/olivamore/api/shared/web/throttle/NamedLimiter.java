package com.olivamore.api.shared.web.throttle;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Adlandirilmis istek limiti: istege gore bir ya da birden cok {@link Limit} (orn. e-posta ve adres basina).
 * Bean olarak tanimlanir, endpoint'e {@code @Throttle("login")} ile baglanir.
 */
public interface NamedLimiter {

	String name();

	List<Limit> limits(HttpServletRequest request);

}
