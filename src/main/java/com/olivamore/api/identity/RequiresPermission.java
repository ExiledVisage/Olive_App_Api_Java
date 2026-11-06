package com.olivamore.api.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Laravel'in {@code ->middleware('permission:order.view')}'i (Spatie PermissionMiddleware):
 * kullanici bu izinlerden birine sahip olmali (dogrudan ya da rolu uzerinden), yoksa 403
 * {@code "User does not have the right permissions."}. {@code admin} rolu her izne sahiptir
 * ({@code Gate::before}).
 * Sinifta ve metotta birlikte kullanilirsa ikisi de gecilmeli (once sinif).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

	/** Herhangi biri yeterli ({@code permission:a|b}). */
	String[] value();

}
