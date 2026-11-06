package com.olivamore.api.shared.web.throttle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Laravel'in {@code ->middleware('throttle:...')}'i.
 * <ul>
 *   <li>{@code @Throttle("login")}: adlandirilmis limiter ({@link NamedLimiter} bean'i)</li>
 *   <li>{@code @Throttle(maxAttempts = 30, decayMinutes = 1)}: {@code throttle:30,1}; anahtar giris yapan
 *       kullanici, yoksa istemci adresi</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Throttle {

	/** Adlandirilmis limiter. Bossa {@link #maxAttempts()} / {@link #decayMinutes()} kullanilir. */
	String value() default "";

	int maxAttempts() default 60;

	int decayMinutes() default 1;

}
