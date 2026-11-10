package com.olivamore.api.identity.security;

import com.olivamore.api.identity.auth.TokenService;
import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Public;
import com.olivamore.api.shared.error.HttpStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Laravel'in {@code auth:sanctum}'u. {@code /api/**} altindaki her endpoint {@link Public} degilse
 * giris ister, yoksa 401 {@code {"message": "Unauthenticated."}}.
 * <p>
 * Handler bulunduktan sonra calisir: olmayan route Laravel'deki gibi tokensiz de 404, yanlis metot 405 doner.
 * Rol/izin kontrolu ({@link AccessAspect}) daha sonra yapilir.
 */
class AccessInterceptor implements HandlerInterceptor {

	private final TokenService tokens;

	AccessInterceptor(TokenService tokens) {
		this.tokens = tokens;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), Public.class)
			|| AnnotatedElementUtils.hasAnnotation(method.getBeanType(), Public.class)) {
			return true;
		}
		Optional<CurrentUser> user = tokens.authenticate(request.getHeader("Authorization"));
		if (user.isEmpty()) {
			throw HttpStatusException.unauthenticated();
		}
		request.setAttribute(Access.ATTRIBUTE, user.get());
		return true;
	}

}
