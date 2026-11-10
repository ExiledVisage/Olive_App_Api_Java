package com.olivamore.api.shared.web;

import com.olivamore.api.shared.error.ApiMessages;
import com.olivamore.api.shared.web.throttle.NamedLimiter;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import com.olivamore.api.shared.web.throttle.RateLimitInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Istek filtreleri, sayfalama ve rate limit. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final ObjectProvider<NamedLimiter> limiters;
	private final ObjectProvider<RateLimitInterceptor.UserKey> userKey;
	private final RateLimitBuckets buckets = new RateLimitBuckets();
	private final int maxRequestBodyBytes;
	private final ApiMessages messages;
	private final JsonMapper json;
	private final int maxPageSize;

	public WebConfig(ObjectProvider<NamedLimiter> limiters, ObjectProvider<RateLimitInterceptor.UserKey> userKey,
		@Value("${olive.http.max-request-body}") DataSize maxRequestBody, ApiMessages messages, JsonMapper json,
		@Value("${spring.data.web.pageable.max-page-size}") int maxPageSize) {
		this.limiters = limiters;
		this.userKey = userKey;
		this.maxRequestBodyBytes = Math.toIntExact(maxRequestBody.toBytes());
		this.messages = messages;
		this.json = json;
		this.maxPageSize = maxPageSize;
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
			new FilterRegistrationBean<>(new RequestBodyLimitFilter(maxRequestBodyBytes,
				messages.text("{error.content-too-large}"), json));
		registration.addUrlPatterns("/api/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 12);
		return registration;
	}

	/** Sayfa parametresi verilmezse butun kayitlar ({@link Pages}); en buyuk sayfa boyutu application.yaml'da. */
	@Bean
	PageableHandlerMethodArgumentResolverCustomizer unpagedByDefault() {
		return resolver -> {
			resolver.setFallbackPageable(Pageable.unpaged());
			resolver.setMaxPageSize(maxPageSize);
		};
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
		registry.addInterceptor(new PageParameters(maxPageSize)).addPathPatterns("/api/**");
	}

}
