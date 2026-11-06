package com.olivamore.api.shared.web;

import com.olivamore.api.shared.json.LaravelStringDeserializer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Laravel'in {@code TrimStrings} ve {@code ConvertEmptyStringsToNull}
 * middleware'lerinin query parametreleri icin karsiligi (JSON govdesi icin:
 * {@link LaravelStringDeserializer}).
 * <ul>
 *   <li>degerler kirpilir (sifre alanlari haric)</li>
 *   <li>bos deger yok sayilir: {@code ?search=} hic gelmemis gibi davranir.
 *       Laravel'de deger null olur; sayfalama linklerine de yazilmaz.</li>
 *   <li>ayni parametre birden fazla gelirse PHP gibi son deger gecerlidir,
 *       sirasi ilk goruldugu yerdir: {@code ?b=1&a=2&b=3 -> b=3, a=2}</li>
 * </ul>
 * Laravel'in validation'i bos degeri de "gelmis ama null" sayar
 * ({@code per_page=} iki hata verir). Bunu gerektiren yerler
 * {@link #wasSent(HttpServletRequest, String)} ile kontrol eder.
 * <p>
 * Bean olarak degil {@link WebConfig}'te FilterRegistrationBean ile kaydedilir: Spring Modulith
 * insight modul bean'lerini proxy'liyor, filtrenin final init() metodu proxy uzerinden
 * calisinca logger null kaliyor.
 */
public class InputNormalizationFilter extends OncePerRequestFilter {

	private static final String RAW_NAMES = InputNormalizationFilter.class.getName() + ".rawNames";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		request.setAttribute(RAW_NAMES, Collections.unmodifiableSet(request.getParameterMap().keySet()));
		chain.doFilter(new NormalizedRequest(request), response);
	}

	/** Parametre istekte var miydi (bos olsa bile)? Laravel'in "present" kavrami. */
	public static boolean wasSent(HttpServletRequest request, String name) {
		Object names = request.getAttribute(RAW_NAMES);
		return names instanceof java.util.Set<?> set ? set.contains(name) : request.getParameter(name) != null;
	}

	private static final class NormalizedRequest extends HttpServletRequestWrapper {

		private final Map<String, String[]> parameters;

		NormalizedRequest(HttpServletRequest request) {
			super(request);
			Map<String, String[]> normalized = new LinkedHashMap<>();
			request.getParameterMap().forEach((name, values) -> {
				String last = values.length == 0 ? null : values[values.length - 1];
				String value = LaravelStringDeserializer.normalize(last,
					LaravelStringDeserializer.NOT_TRIMMED.contains(name));
				if (value != null) {
					normalized.put(name, new String[] {value});
				}
			});
			this.parameters = Collections.unmodifiableMap(normalized);
		}

		@Override
		public String getParameter(String name) {
			String[] values = parameters.get(name);
			return values == null ? null : values[0];
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			return parameters;
		}

		@Override
		public Enumeration<String> getParameterNames() {
			return Collections.enumeration(parameters.keySet());
		}

		@Override
		public String[] getParameterValues(String name) {
			return parameters.get(name);
		}

	}

}
