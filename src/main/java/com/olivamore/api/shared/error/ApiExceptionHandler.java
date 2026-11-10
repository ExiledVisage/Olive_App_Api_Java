package com.olivamore.api.shared.error;

import com.olivamore.api.shared.json.Naming;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Violation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.lang.reflect.Field;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Butun hatalar RFC 9457 {@link ProblemDetail} olarak doner ({@code application/problem+json}):
 * <pre>{"title": "Unprocessable Content", "status": 422, "instance": "/api/auth/login",
 *  "detail": "The email field is required. (and 1 more error)",
 *  "errors": {"email": ["The email field is required."], "password": ["..."]}}</pre>
 * {@code errors} yalnizca alan hatalarinda vardir; alan yolu JSON'daki gibidir ({@code items.0.quantity}).
 * Spring {@code instance}'i istegin yoluyla doldurur; {@code type} varsayilan oldugu icin yazilmaz.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

	/** 405 mesajinda metotlarin sirasi. */
	private static final List<String> METHOD_ORDER = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

	private final ApiMessages messages;

	public ApiExceptionHandler(ApiMessages messages) {
		this.messages = messages;
	}

	// ---- 422 ---------------------------------------------------------------

	@ExceptionHandler(ValidationFailedException.class)
	ResponseEntity<ProblemDetail> validation(ValidationFailedException e) {
		return unprocessable(e.violations());
	}

	@ExceptionHandler(BusinessRuleException.class)
	ResponseEntity<ProblemDetail> businessRule(BusinessRuleException e) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
	}

	/** {@code @Valid @RequestBody} ve {@code @Valid @ModelAttribute} (query parametreleri) hatalari. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException e) {
		List<String> order = declaredOrder(e.getParameter().getNestedParameterType());
		List<FieldError> fieldErrors = new ArrayList<>(e.getBindingResult().getFieldErrors());
		fieldErrors.sort(Comparator.<FieldError>comparingInt(fe -> position(order, fe.getField()))
			.thenComparing(FieldError::getField));
		List<Violation> violations = new ArrayList<>();
		for (FieldError fe : fieldErrors) {
			String message = fe.isBindingFailure() ? typeMessage(bindingTargetType(fe)) : String.valueOf(fe.getDefaultMessage());
			violations.add(Violation.of(jsonPath(fe.getField()), message));
		}
		return unprocessable(violations);
	}

	/** {@code @RequestParam} / {@code @PathVariable} uzerindeki constraint hatalari. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ProblemDetail> invalidParameters(HandlerMethodValidationException e) {
		List<Violation> violations = new ArrayList<>();
		e.getParameterValidationResults().forEach(result -> {
			String field = Naming.snake(String.valueOf(result.getMethodParameter().getParameterName()));
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				violations.add(Violation.of(field, String.valueOf(error.getDefaultMessage())));
			}
		});
		return unprocessable(violations);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException e) {
		return unprocessable(List.of(Violation.of(e.getParameterName(), MessageKeys.REQUIRED)));
	}

	/** Query/path parametresi yanlis tipte: {@code ?customer_id=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> parameterType(MethodArgumentTypeMismatchException e) {
		return unprocessable(List.of(Violation.of(e.getName(), typeMessage(e.getRequiredType()))));
	}

	/** JSON govdesi okunamadi: alan yanlis tipteyse 422, JSON'un kendisi bozuksa 400. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e) {
		if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
			return unprocessable(List.of(Violation.of(jsonPath(mismatch.getPath()), typeMessage(mismatch.getTargetType()))));
		}
		return problem(HttpStatus.BAD_REQUEST, messages.text("{error.malformed-json}"));
	}

	/** 401, 403, 404, 429 ve alan bagimsiz 422: yalnizca mesaj (+ varsa basliklar). */
	@ExceptionHandler(HttpStatusException.class)
	ResponseEntity<ProblemDetail> httpStatus(HttpStatusException e) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.status());
		e.headers().forEach(builder::header);
		return builder.body(Problems.of(HttpStatus.valueOf(e.status()), messages.text(e.getMessage())));
	}

	// ---- 404 / 405 / 415 ---------------------------------------------------

	@ExceptionHandler(ModelNotFoundException.class)
	ResponseEntity<ProblemDetail> modelNotFound(ModelNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND,
			messages.text("{error.not-found}", Map.of("resource", e.resource(), "id", String.valueOf(e.id()))));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ProblemDetail> routeNotFound(NoResourceFoundException e, HttpServletRequest request) {
		return problem(HttpStatus.NOT_FOUND, messages.text("{error.route-not-found}", Map.of("path", routePath(request))));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
		Set<String> supported = new LinkedHashSet<>();
		if (e.getSupportedHttpMethods() != null) {
			e.getSupportedHttpMethods().stream().map(HttpMethod::name).forEach(supported::add);
		}
		// GET destekleyen her yol HEAD'i de destekler
		if (supported.contains("GET")) {
			supported.add("HEAD");
		}
		List<String> methods = supported.stream()
			.sorted(Comparator.comparingInt(m -> METHOD_ORDER.contains(m) ? METHOD_ORDER.indexOf(m) : METHOD_ORDER.size()))
			.toList();
		return problem(HttpStatus.METHOD_NOT_ALLOWED, messages.text("{error.method-not-allowed}",
			Map.of("method", e.getMethod(), "path", routePath(request), "methods", String.join(", ", methods))));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ProblemDetail> unsupportedMediaType() {
		return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, messages.text("{error.unsupported-media-type}"));
	}

	// ---- 500 ---------------------------------------------------------------

	/** Ic ayrinti (istisna, SQL) cevaba yazilmaz, yalnizca loglanir. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> serverError(Exception e, HttpServletRequest request) {
		log.error("Beklenmeyen hata: {} {}", request.getMethod(), request.getRequestURI(), e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, messages.text("{error.server}"));
	}

	// ---- yardimcilar -------------------------------------------------------

	/** Alan hatalari: ust {@code detail} ilk hata, digerleri sayi olarak. */
	private ResponseEntity<ProblemDetail> unprocessable(List<Violation> violations) {
		Map<String, List<String>> errors = new LinkedHashMap<>();
		for (Violation violation : violations) {
			errors.computeIfAbsent(violation.field(), k -> new ArrayList<>()).add(messages.text(violation));
		}
		List<String> all = errors.values().stream().flatMap(List::stream).toList();
		String detail;
		if (all.isEmpty()) {
			detail = messages.text("{validation.summary.none}");
		}
		else if (all.size() == 1) {
			detail = all.getFirst();
		}
		else {
			detail = messages.text(all.size() == 2 ? "{validation.summary.more-one}" : "{validation.summary.more}",
				Map.of("message", all.getFirst(), "count", all.size() - 1));
		}
		ProblemDetail problem = Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, detail);
		problem.setProperty("errors", errors);
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
		return ResponseEntity.status(status).body(Problems.of(status, detail));
	}

	/** Istegin yolu, bastaki ve sondaki '/' olmadan. */
	private static String routePath(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		path = path.replaceAll("^/+|/+$", "");
		return path.isEmpty() ? "/" : path;
	}

	/** {@code items[0].unitPrice -> items.0.unit_price} */
	static String jsonPath(String springPath) {
		String dotted = springPath.replaceAll("\\[(\\d+)]", ".$1");
		List<String> parts = new ArrayList<>();
		for (String part : dotted.split("\\.")) {
			parts.add(Naming.snake(part));
		}
		return String.join(".", parts);
	}

	private static String jsonPath(List<JacksonException.Reference> path) {
		List<String> parts = new ArrayList<>();
		for (JacksonException.Reference ref : path) {
			parts.add(ref.getPropertyName() != null ? ref.getPropertyName() : String.valueOf(ref.getIndex()));
		}
		return String.join(".", parts);
	}

	/** Hedef Java tipine gore tip kurali mesaji. */
	private static String typeMessage(Class<?> type) {
		if (type == null) {
			return MessageKeys.IN;
		}
		if (type == Integer.class || type == int.class || type == Long.class || type == long.class
			|| type == Short.class || type == short.class) {
			return MessageKeys.INTEGER;
		}
		if (Number.class.isAssignableFrom(type) || type == double.class || type == float.class) {
			return MessageKeys.NUMERIC;
		}
		if (type == Boolean.class || type == boolean.class) {
			return MessageKeys.BOOLEAN;
		}
		if (type == String.class) {
			return MessageKeys.STRING;
		}
		if (type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) || type.isRecord()) {
			return MessageKeys.ARRAY;
		}
		if (Temporal.class.isAssignableFrom(type)) {
			return MessageKeys.DATE;
		}
		return MessageKeys.IN;
	}

	/** Tip donusumu hatasinda hedef tip: Spring hata kodlarina yazar ({@code typeMismatch.java.lang.Long}). */
	private static Class<?> bindingTargetType(FieldError fe) {
		if (fe.getCodes() != null) {
			for (String code : fe.getCodes()) {
				if (code.startsWith("typeMismatch.java.")) {
					try {
						return Class.forName(code.substring("typeMismatch.".length()));
					}
					catch (ClassNotFoundException ignored) {
						// sonraki kod denenir
					}
				}
			}
		}
		return null;
	}

	/** Istek record'undaki alanlarin tanim sirasi (snake_case): hatalar bu sirada doner. */
	private static List<String> declaredOrder(Class<?> type) {
		List<String> names = new ArrayList<>();
		for (Field field : type.getDeclaredFields()) {
			names.add(Naming.snake(field.getName()));
		}
		return names;
	}

	private static int position(List<String> order, String springPath) {
		String top = Naming.snake(springPath.split("[.\\[]", 2)[0]);
		int index = order.indexOf(top);
		return index < 0 ? order.size() : index;
	}

}
