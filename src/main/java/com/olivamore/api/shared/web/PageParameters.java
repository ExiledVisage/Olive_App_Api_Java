package com.olivamore.api.shared.web;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Sayfali endpoint'lerde {@code page} ve {@code size}: tamsayi, {@code page >= 0}, {@code 1 <= size <= en buyuk
 * sayfa}; varsayilan sayfasi olmayan endpoint'lerde ikisi birlikte verilir. Gecersiz deger 422 alir (Spring Data'nin
 * cozumleyicisi gecersiz degeri sessizce yok sayar ya da butun kayitlara duser).
 */
class PageParameters implements HandlerInterceptor {

	static final String PAGE = "page";
	static final String SIZE = "size";

	private final int maxSize;

	PageParameters(int maxSize) {
		this.maxSize = maxSize;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		MethodParameter pageable = Arrays.stream(method.getMethodParameters())
			.filter(p -> Pageable.class.equals(p.getParameterType())).findFirst().orElse(null);
		if (pageable == null) {
			return true;
		}
		String page = request.getParameter(PAGE);
		String size = request.getParameter(SIZE);
		ValidationErrors errors = new ValidationErrors();
		check(errors, PAGE, page, 0, Integer.MAX_VALUE);
		check(errors, SIZE, size, 1, maxSize);
		if (!pageable.hasParameterAnnotation(PageableDefault.class)) {
			if (isGiven(size) && !isGiven(page)) {
				errors.add(PAGE, MessageKeys.REQUIRED_WITH, "other", SIZE);
			}
			if (isGiven(page) && !isGiven(size)) {
				errors.add(SIZE, MessageKeys.REQUIRED_WITH, "other", PAGE);
			}
		}
		errors.throwIfAny();
		return true;
	}

	private static void check(ValidationErrors errors, String field, String value, int min, int max) {
		if (!isGiven(value)) {
			return;
		}
		try {
			int number = Integer.parseInt(value.strip());
			if (number < min) {
				errors.add(field, MessageKeys.MIN, "value", min);
			}
			if (number > max) {
				errors.add(field, MessageKeys.MAX, "value", max);
			}
		}
		catch (NumberFormatException e) {
			errors.add(field, MessageKeys.INTEGER);
		}
	}

	private static boolean isGiven(String value) {
		return value != null && !value.isBlank();
	}

}
