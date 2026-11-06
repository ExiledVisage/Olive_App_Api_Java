package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Istekteki giris yapmis kullanici (AccessInterceptor yazar). */
final class Access {

	static final String ATTRIBUTE = CurrentUser.class.getName();

	private Access() {
	}

	static CurrentUser current(HttpServletRequest request) {
		return (CurrentUser) request.getAttribute(ATTRIBUTE);
	}

	static CurrentUser current() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servlet) {
			return current(servlet.getRequest());
		}
		return null;
	}

}
