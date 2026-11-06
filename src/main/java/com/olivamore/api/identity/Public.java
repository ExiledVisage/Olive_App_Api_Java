package com.olivamore.api.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Giris gerektirmeyen endpoint (Laravel'de {@code auth:sanctum} olmayan route).
 * {@code /api/**} altindaki her endpoint bu isaret yoksa giris ister.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Public {
}
