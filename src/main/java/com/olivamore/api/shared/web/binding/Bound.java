package com.olivamore.api.shared.web.binding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Laravel'in route model binding'i: path degiskenindeki id'den entity yuklenir, yoksa 404
 * ({@code No query results for model [App\Models\User] 999}).
 * <pre>
 * &#64;GetMapping("/api/users/{user}")
 * UserResource show(&#64;Bound User user)
 * </pre>
 * Laravel'deki sira korunur: once kimlik (401), sonra binding (404), sonra yetki (403).
 * Entity'nin Laravel model adi {@link LaravelModel} ile verilir.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Bound {

	/** Path degiskeninin adi; bossa parametre adi. */
	String value() default "";

}
