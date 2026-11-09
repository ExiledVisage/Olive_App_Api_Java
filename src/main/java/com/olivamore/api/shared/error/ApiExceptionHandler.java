package com.olivamore.api.shared.error;

import com.olivamore.api.shared.json.Naming;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
 * Butun hatalari Laravel'in JSON cevaplarina birebir cevirir. Ornekler
 * Laravel'den yakalandi (bkz. reference/README.md):
 * <ul>
 *   <li>422 {@code {"message": "...", "errors": {"alan": ["..."]}}}</li>
 *   <li>404 {@code {"message": "No query results for model [App\\Models\\Order] 999999"}}</li>
 *   <li>404 {@code {"message": "The route api/x could not be found."}}</li>
 *   <li>405 {@code {"message": "The PATCH method is not supported for route api/auth/login. Supported methods: POST."}}</li>
 *   <li>500 {@code {"message": "Server Error"}}</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

	/** Laravel'in 405 mesajinda metotlarin sirasi (route tanim sirasi, resource route'lar icin bu). */
	private static final List<String> METHOD_ORDER = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

	// ---- 422 ---------------------------------------------------------------

	@ExceptionHandler(LaravelValidationException.class)
	ResponseEntity<Map<String, Object>> validation(LaravelValidationException e) {
		return unprocessable(e.errors());
	}

	@ExceptionHandler(BusinessRuleException.class)
	ResponseEntity<Map<String, Object>> businessRule(BusinessRuleException e) {
		// errors bossa Laravel {} yazar ([] degil): bos LinkedHashMap de {} olarak serilestirilir.
		return body(HttpStatus.UNPROCESSABLE_CONTENT, "message", e.getMessage(), "errors", e.errors());
	}

	/**
	 * {@code @Valid @RequestBody} ve {@code @Valid @ModelAttribute} (query parametreleri) hatalari.
	 * Zorunlu query parametreleri {@code @RequestParam} ile degil, bir record'da toplanip
	 * {@code @Valid @ModelAttribute} ile alinir: Laravel butun eksikleri tek cevapta dondurur,
	 * {@code @RequestParam} ise ilk eksikte durur.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException e) {
		List<String> order = declaredOrder(e.getParameter().getNestedParameterType());

		List<FieldError> fieldErrors = new ArrayList<>(e.getBindingResult().getFieldErrors());
		fieldErrors.sort(Comparator.comparingInt(fe -> position(order, fe.getField())));

		Map<String, List<String>> errors = new LinkedHashMap<>();
		for (FieldError fe : fieldErrors) {
			String field = laravelPath(fe.getField());
			String template = fe.isBindingFailure() ? typeMessage(bindingTargetType(fe)) : String.valueOf(fe.getDefaultMessage());
			add(errors, field, LaravelMessages.format(template, field));
		}
		return unprocessable(errors);
	}

	/** {@code @RequestParam} / {@code @PathVariable} uzerindeki constraint hatalari. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<Map<String, Object>> invalidParameters(HandlerMethodValidationException e) {
		Map<String, List<String>> errors = new LinkedHashMap<>();
		e.getParameterValidationResults().forEach(result -> {
			String field = Naming.snake(String.valueOf(result.getMethodParameter().getParameterName()));
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				add(errors, field, LaravelMessages.format(String.valueOf(error.getDefaultMessage()), field));
			}
		});
		return unprocessable(errors);
	}

	/** Zorunlu query parametresi yok: Laravel'de bu bir {@code required} kuralidir. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<Map<String, Object>> missingParameter(MissingServletRequestParameterException e) {
		String field = e.getParameterName();
		return unprocessable(Map.of(field, List.of(LaravelMessages.format(LaravelMessages.REQUIRED, field))));
	}

	/** Query/path parametresi yanlis tipte: {@code ?customer_id=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Map<String, Object>> parameterType(MethodArgumentTypeMismatchException e) {
		String field = e.getName();
		return unprocessable(Map.of(field, List.of(LaravelMessages.format(typeMessage(e.getRequiredType()), field))));
	}

	/**
	 * JSON govdesi okunamadi. Alan yanlis tipteyse (sayi yerine metin gibi)
	 * Laravel'in tip kurali mesaji uretilir. JSON'un kendisi bozuksa Laravel
	 * govdeyi bos sayip validation'a devam eder; bu birebir kopyalanamadigi
	 * icin 400 donulur (bilinen fark, bkz. docs/laravel-reference/DEVIATIONS.md).
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
		if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
			String field = jsonPath(mismatch.getPath());
			return unprocessable(Map.of(field, List.of(LaravelMessages.format(typeMessage(mismatch.getTargetType()), field))));
		}
		return body(HttpStatus.BAD_REQUEST, "message", "Malformed JSON.");
	}

	/** {@code abort($status, $message)}, 401, 403, 429: yalnizca mesaj (+ varsa basliklar). */
	@ExceptionHandler(HttpStatusException.class)
	ResponseEntity<Map<String, Object>> httpStatus(HttpStatusException e) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.status());
		e.headers().forEach(builder::header);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", e.getMessage());
		return builder.body(body);
	}

	// ---- 404 / 405 ---------------------------------------------------------

	@ExceptionHandler(ModelNotFoundException.class)
	ResponseEntity<Map<String, Object>> modelNotFound(ModelNotFoundException e) {
		return body(HttpStatus.NOT_FOUND, "message", e.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<Map<String, Object>> routeNotFound(NoResourceFoundException e, HttpServletRequest request) {
		return body(HttpStatus.NOT_FOUND, "message", "The route " + laravelRoutePath(request) + " could not be found.");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<Map<String, Object>> methodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
		Set<String> supported = new LinkedHashSet<>();
		if (e.getSupportedHttpMethods() != null) {
			e.getSupportedHttpMethods().stream().map(HttpMethod::name).forEach(supported::add);
		}
		// Laravel her GET route'unu HEAD ile birlikte kaydeder
		if (supported.contains("GET")) {
			supported.add("HEAD");
		}
		List<String> methods = supported.stream()
			.sorted(Comparator.comparingInt(m -> METHOD_ORDER.contains(m) ? METHOD_ORDER.indexOf(m) : METHOD_ORDER.size()))
			.toList();
		return body(HttpStatus.METHOD_NOT_ALLOWED, "message", "The " + e.getMethod() + " method is not supported for route "
			+ laravelRoutePath(request) + ". Supported methods: " + String.join(", ", methods) + ".");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<Map<String, Object>> unsupportedMediaType() {
		return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "message", "Unsupported Media Type.");
	}

	// ---- 500 ---------------------------------------------------------------

	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, Object>> serverError(Exception e, HttpServletRequest request) {
		log.error("Beklenmeyen hata: {} {}", request.getMethod(), request.getRequestURI(), e);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "message", "Server Error");
	}

	// ---- yardimcilar -------------------------------------------------------

	private static ResponseEntity<Map<String, Object>> unprocessable(Map<String, List<String>> errors) {
		return body(HttpStatus.UNPROCESSABLE_CONTENT, "message", LaravelMessages.summary(errors), "errors", errors);
	}

	private static ResponseEntity<Map<String, Object>> body(HttpStatus status, Object... keyValues) {
		Map<String, Object> body = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			body.put((String) keyValues[i], keyValues[i + 1]);
		}
		return ResponseEntity.status(status).body(body);
	}

	private static void add(Map<String, List<String>> errors, String field, String message) {
		errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
	}

	/** Laravel'in {@code $request->path()}'i: bastaki ve sondaki '/' olmadan. */
	private static String laravelRoutePath(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		path = path.replaceAll("^/+|/+$", "");
		return path.isEmpty() ? "/" : path;
	}

	/** {@code items[0].unitPrice -> items.0.unit_price} */
	static String laravelPath(String springPath) {
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

	/** Hedef Java tipine gore Laravel'in tip kurali mesaji. */
	private static String typeMessage(Class<?> type) {
		if (type == null) {
			return LaravelMessages.IN;
		}
		if (type == Integer.class || type == int.class || type == Long.class || type == long.class
			|| type == Short.class || type == short.class) {
			return LaravelMessages.INTEGER;
		}
		if (Number.class.isAssignableFrom(type) || type == double.class || type == float.class) {
			return LaravelMessages.NUMERIC;
		}
		if (type == Boolean.class || type == boolean.class) {
			return LaravelMessages.BOOLEAN;
		}
		if (type == String.class) {
			return LaravelMessages.STRING;
		}
		if (type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
			return LaravelMessages.ARRAY;
		}
		if (Temporal.class.isAssignableFrom(type)) {
			return LaravelMessages.DATE;
		}
		return LaravelMessages.IN;
	}

	/**
	 * Tip donusumu hatasinda hedef tip. Spring hata kodlarina tipi yazar:
	 * {@code typeMismatch.java.lang.Long}.
	 */
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

	/**
	 * Request DTO'sundaki alanlarin tanim sirasi (snake_case). Laravel hatalari kurallarin
	 * yazildigi sirada dondurur; DTO alanlari da ayni sirada yazilir.
	 */
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
