package com.olivamore.api.shared.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Laravel uyumlu istek isleme. */
@Configuration(proxyBeanMethods = false)
public class WebConfig {

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

}
