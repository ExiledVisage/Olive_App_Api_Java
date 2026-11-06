package com.olivamore.api.shared.web.binding;

import com.olivamore.api.shared.error.ModelNotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/** {@link Bound} parametrelerini cozer. */
public class BoundModelResolver implements HandlerMethodArgumentResolver {

	private final EntityManager entityManager;

	public BoundModelResolver(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(Bound.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
		WebDataBinderFactory binderFactory) {
		Bound bound = parameter.getParameterAnnotation(Bound.class);
		String name = bound != null && !bound.value().isEmpty() ? bound.value() : parameter.getParameterName();
		@SuppressWarnings("unchecked")
		Map<String, String> variables = (Map<String, String>) request.getAttribute(
			HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		String raw = variables == null ? null : variables.get(name);
		if (raw == null) {
			throw new IllegalStateException("Path degiskeni yok: {" + name + "}");
		}
		Class<?> type = parameter.getParameterType();
		Object entity = entityManager.find(type, id(raw));
		if (entity == null) {
			throw new ModelNotFoundException(laravelName(type), raw);
		}
		return entity;
	}

	/**
	 * Laravel {@code where id = ?} sorgusunu PostgreSQL'e metinle gonderir; sayi olmayan deger
	 * bigint kolonda SQL hatasina (500) yol acar. Ayni sonuc icin burada da hata.
	 */
	private static Long id(String raw) {
		try {
			return Long.valueOf(raw);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("invalid input syntax for type bigint: \"" + raw + "\"", e);
		}
	}

	static String laravelName(Class<?> type) {
		LaravelModel model = type.getAnnotation(LaravelModel.class);
		return model != null ? model.value() : type.getSimpleName();
	}

}
