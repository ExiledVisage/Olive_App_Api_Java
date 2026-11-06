package com.olivamore.api.shared.web.binding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Entity'nin Laravel'deki model sinifi; 404 mesajinda gecer.
 * <pre>
 * &#64;LaravelModel("User")                              -> App\Models\User
 * &#64;LaravelModel("Spatie\\Permission\\Models\\Role")  -> oldugu gibi
 * </pre>
 * Yoksa Java sinif adi kullanilir ({@code App\Models\<SinifAdi>}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LaravelModel {

	String value();

}
