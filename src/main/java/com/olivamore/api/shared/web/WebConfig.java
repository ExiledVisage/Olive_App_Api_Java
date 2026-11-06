package com.olivamore.api.shared.web;

import com.olivamore.api.shared.web.binding.BoundModelResolver;
import com.olivamore.api.shared.web.throttle.NamedLimiter;
import com.olivamore.api.shared.web.throttle.RateLimiter;
import com.olivamore.api.shared.web.throttle.ThrottleInterceptor;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;

/** Laravel uyumlu istek isleme. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	/** Interceptor sirasi: Laravel'in middleware onceligi (Authenticate, ThrottleRequests). */
	public static final int ORDER_AUTHENTICATE = 0;
	public static final int ORDER_THROTTLE = 10;

	private final ObjectProvider<EntityManager> entityManager;
	private final ObjectProvider<NamedLimiter> limiters;
	private final ObjectProvider<ThrottleInterceptor.UserKey> userKey;
	private final RateLimiter rateLimiter = new RateLimiter(Clock.systemUTC());

	public WebConfig(ObjectProvider<EntityManager> entityManager, ObjectProvider<NamedLimiter> limiters,
		ObjectProvider<ThrottleInterceptor.UserKey> userKey) {
		this.entityManager = entityManager;
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

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		EntityManager em = entityManager.getIfAvailable();
		if (em != null) {
			resolvers.add(new BoundModelResolver(em));
		}
		resolvers.add(new RequestInputResolver());
	}

	/** Controller parametresi olarak {@link RequestInput}. */
	static final class RequestInputResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return parameter.getParameterType() == RequestInput.class;
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
			WebDataBinderFactory binderFactory) {
			return RequestInput.of(request.getNativeRequest(HttpServletRequest.class));
		}

	}

}
