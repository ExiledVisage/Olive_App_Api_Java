package com.olivamore.api.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API cevaplari onbelleklenmez: her cevaba varsayilan olarak {@code Cache-Control: no-cache, private} yazilir.
 * Farkli deger isteyen endpoint (orn. APK indirme: {@code public}) basligi kendisi ezer.
 * <p>
 * WebConfig'te FilterRegistrationBean ile kaydedilir: bean olsaydi Spring Modulith insight proxy'lerdi.
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
