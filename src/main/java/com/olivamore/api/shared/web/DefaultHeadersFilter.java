package com.olivamore.api.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Laravel (Symfony Response) her cevaba, baska bir deger verilmemisse
 * {@code Cache-Control: no-cache, private} yazar. Farkli deger isteyen endpoint (orn. APK indirme:
 * {@code public}) basligi kendisi ezer.
 * <p>
 * WebConfig'te FilterRegistrationBean ile kaydedilir (bean degil; bkz. {@link InputNormalizationFilter}).
 */
public class DefaultHeadersFilter extends OncePerRequestFilter {

	public static final String CACHE_CONTROL = "no-cache, private";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		response.setHeader("Cache-Control", CACHE_CONTROL);
		chain.doFilter(request, response);
	}

}
