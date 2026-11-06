package com.olivamore.api.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Laravel'in {@code ->middleware('role:admin')}'i (Spatie RoleMiddleware): kullanici bu rollerden
 * birine sahip olmali, yoksa 403 {@code "User does not have the right roles."}.
 * Sinifta ve metotta birlikte kullanilirsa ikisi de gecilmeli (once sinif).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRole {

	/** Herhangi biri yeterli ({@code role:a|b}). */
	String[] value();

}
