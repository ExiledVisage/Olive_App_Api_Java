package com.olivamore.api.shared.error;

import java.util.Map;

/**
 * Belirli bir HTTP durumu ve mesajla reddetme (401, 403, 429 ya da alan bagimsiz 422). Cevap {@code ProblemDetail};
 * mesaj {@code detail} olur, varsa basliklar eklenir. Mesaj {@code {error.unauthenticated}} bicimindeyse
 * {@code messages.properties}'ten cozulur. Alan bazli hatalar icin {@link ValidationFailedException}.
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

	public static HttpStatusException unauthenticated() {
		return new HttpStatusException(401, "{error.unauthenticated}");
	}

	public static HttpStatusException unprocessable(String message) {
		return new HttpStatusException(422, message);
	}

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
