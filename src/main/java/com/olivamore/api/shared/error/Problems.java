package com.olivamore.api.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * RFC 9457 hata cevaplari. Controller disinda (filtreler, guvenlik) yazilan cevaplar da ayni bicimdedir:
 * <pre>{"title": "Not Found", "status": 404, "detail": "Order 7 not found.", "instance": "/api/orders/7"}</pre>
 * {@code type} varsayilan ({@code about:blank}) oldugu icin yazilmaz.
 */
public final class Problems {

	private Problems() {
	}

	/** @param detail bossa cevapta {@code detail} olmaz */
	public static ProblemDetail of(HttpStatusCode status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		if (detail != null && !detail.isEmpty()) {
			problem.setDetail(detail);
		}
		return problem;
	}

	/** Filtre ya da guvenlik katmanindan dogrudan yazar; {@code instance} controller'lardaki gibi istegin yolu. */
	public static void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem,
		JsonMapper json) throws IOException {
		problem.setInstance(URI.create(request.getRequestURI()));
		response.setStatus(problem.getStatus());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(json.writeValueAsString(problem));
	}

}
