package com.olivamore.api.shared.error;

import java.util.Map;

/**
 * Laravel'in {@code abort($status, $message)} karsiligi. Cevap yalnizca mesajdir,
 * {@code errors} anahtari yoktur:
 * <pre>abort(422, 'Admin kullanicisi silinemez.')  ->  422 {"message": "Admin kullanicisi silinemez."}</pre>
 * Alan bazli hatalar icin {@link BusinessRuleException} ya da {@link LaravelValidationException}.
 */
public class HttpStatusException extends RuntimeException {

	private final int status;
	private final Map<String, String> headers;

	public HttpStatusException(int status, String message) {
		this(status, message, Map.of());
	}

	public HttpStatusException(int status, String message, Map<String, String> headers) {
		super(message);
		this.status = status;
		this.headers = Map.copyOf(headers);
	}

	/** {@code abort(422, ...)} */
	public static HttpStatusException unprocessable(String message) {
		return new HttpStatusException(422, message);
	}

	/** auth:sanctum gecemedi */
	public static HttpStatusException unauthenticated() {
		return new HttpStatusException(401, "Unauthenticated.");
	}

	/** Spatie: role/permission middleware gecemedi */
	public static HttpStatusException forbidden(String message) {
		return new HttpStatusException(403, message);
	}

	public int status() {
		return status;
	}

	public Map<String, String> headers() {
		return headers;
	}

}
