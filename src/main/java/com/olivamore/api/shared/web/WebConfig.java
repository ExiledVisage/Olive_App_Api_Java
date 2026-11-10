package com.olivamore.api.shared.web;

import com.olivamore.api.shared.web.throttle.NamedLimiter;
import com.olivamore.api.shared.web.throttle.RateLimiter;
import com.olivamore.api.shared.web.throttle.ThrottleInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;

/** Istek filtreleri ve rate limit. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	/** Interceptor sirasi: once kimlik, sonra rate limit. */
	public static final int ORDER_AUTHENTICATE = 0;
	public static final int ORDER_THROTTLE = 10;

	private final ObjectProvider<NamedLimiter> limiters;
	private final ObjectProvider<ThrottleInterceptor.UserKey> userKey;
	private final RateLimiter rateLimiter = new RateLimiter(Clock.systemUTC());

	public WebConfig(ObjectProvider<NamedLimiter> limiters, ObjectProvider<ThrottleInterceptor.UserKey> userKey) {
		this.limiters = limiters;
		this.userKey = userKey;
	}

	/**
	 * Filtre bean degil, kayit nesnesinin icinde duz bir nesne: Modulith insight
	 * proxy'lemesin (bkz. {@link InputNormalizationFilter}).
	 */
	@Bean
	FilterRegistrationBean<InputNormalizationFilter> inputNormalizationFilter() {
		FilterRegistrationBean<InputNormalizationFilter> registration =
			new FilterRegistrationBean<>(new InputNormalizationFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}

	@Bean
	FilterRegistrationBean<DefaultHeadersFilter> defaultHeadersFilter() {
		FilterRegistrationBean<DefaultHeadersFilter> registration =
			new FilterRegistrationBean<>(new DefaultHeadersFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
		return registration;
	}

	/** Tek ornek: interceptor ve testler ayni sayaclari gorur. */
	@Bean
	RateLimiter rateLimiter() {
		return rateLimiter;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		ThrottleInterceptor.UserKey key = userKey.getIfAvailable(() -> request -> null);
		registry.addInterceptor(new ThrottleInterceptor(rateLimiter, List.copyOf(limiters.orderedStream().toList()), key))
			.addPathPatterns("/api/**")
			.order(ORDER_THROTTLE);
	}

}
