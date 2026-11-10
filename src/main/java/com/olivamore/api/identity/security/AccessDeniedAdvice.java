package com.olivamore.api.identity.security;

import com.olivamore.api.shared.error.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @PreAuthorize} controller metodunda reddederse. Genel hata isleyicisinin {@code Exception} yakalamasindan
 * once calismali, yoksa 500 olur.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccessDeniedAdvice {

	private static final String WRONG_ROLES = "User does not have the right roles.";
	static final String WRONG_PERMISSIONS = "User does not have the right permissions.";

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> denied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Problems.of(HttpStatus.FORBIDDEN, message(e)));
	}

	private static String message(AccessDeniedException e) {
		if (e instanceof AuthorizationDeniedException denied
			&& denied.getAuthorizationResult() instanceof ExpressionAuthorizationDecision decision
			&& decision.getExpression().getExpressionString().contains("hasRole")) {
			return WRONG_ROLES;
		}
		return WRONG_PERMISSIONS;
	}

}
