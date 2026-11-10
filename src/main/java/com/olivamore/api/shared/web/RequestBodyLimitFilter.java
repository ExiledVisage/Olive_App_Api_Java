package com.olivamore.api.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Istek govdesine ust sinir (kimlik dogrulama ve rate limit'ten once calisir). Beyan edilen boyut sinirdan buyukse
 * govde hic okunmaz; beyan yoksa en fazla sinir kadar okunur. Okunan govde tekrar okunabilir: giris limiti
 * e-postayi buradan alir.
 * <p>
 * WebConfig'te FilterRegistrationBean ile kaydedilir (bean degil; bkz. {@link InputNormalizationFilter}).
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

	private static final Set<String> WITHOUT_BODY = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(),
		HttpMethod.OPTIONS.name());

	private final int maxBytes;

	public RequestBodyLimitFilter(int maxBytes) {
		this.maxBytes = maxBytes;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		if (WITHOUT_BODY.contains(request.getMethod()) || request.getContentLengthLong() == 0) {
			chain.doFilter(request, response);
			return;
		}
		if (request.getContentLengthLong() > maxBytes) {
			reject(response);
			return;
		}
		byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
		if (body.length > maxBytes) {
			reject(response);
			return;
		}
		chain.doFilter(new CachedBodyRequest(request, body), response);
	}

	private static void reject(HttpServletResponse response) throws IOException {
		response.setStatus(413);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"Payload Too Large.\"}");
	}

	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream in = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public int read() {
					return in.read();
				}

				@Override
				public int read(byte[] target, int offset, int length) {
					return in.read(target, offset, length);
				}

				@Override
				public boolean isFinished() {
					return in.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}

	}

}
