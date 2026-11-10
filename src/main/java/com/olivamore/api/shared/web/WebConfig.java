package com.olivamore.api.shared.web;

import com.olivamore.api.shared.web.throttle.NamedLimiter;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import com.olivamore.api.shared.web.throttle.RateLimitInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Istek filtreleri ve rate limit. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final ObjectProvider<NamedLimiter> limiters;
	private final ObjectProvider<RateLimitInterceptor.UserKey> userKey;
	private final RateLimitBuckets buckets = new RateLimitBuckets();
	private final int maxRequestBodyBytes;

	public WebConfig(ObjectProvider<NamedLimiter> limiters, ObjectProvider<RateLimitInterceptor.UserKey> userKey,
		@Value("${olive.http.max-request-body}") DataSize maxRequestBody) {
		this.limiters = limiters;
		this.userKey = userKey;
		this.maxRequestBodyBytes = Math.toIntExact(maxRequestBody.toBytes());
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

	@Bean
	FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter() {
		FilterRegistrationBean<RequestBodyLimitFilter> registration =
			new FilterRegistrationBean<>(new RequestBodyLimitFilter(maxRequestBodyBytes));
		registration.addUrlPatterns("/api/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 12);
		return registration;
	}

	/** Tek ornek: interceptor ve testler ayni sayaclari gorur. */
	@Bean
	RateLimitBuckets rateLimitBuckets() {
		return buckets;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		RateLimitInterceptor.UserKey key = userKey.getIfAvailable(() -> request -> null);
		registry.addInterceptor(new RateLimitInterceptor(buckets, List.copyOf(limiters.orderedStream().toList()), key))
			.addPathPatterns("/api/**");
	}

}
