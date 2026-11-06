package com.olivamore.api.shared.web;

import com.olivamore.api.shared.json.LaravelStringDeserializer;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Laravel'in {@code $request->all()}'u: JSON govdesi + query parametreleri (ayni anahtar
 * ikisinde de varsa govde kazanir), {@code TrimStrings} ve {@code ConvertEmptyStringsToNull}
 * uygulanmis halde.
 * <p>
 * Degerler PHP'nin {@code json_decode(..., true)} ciktisina karsilik gelir:
 * nesne -> {@link LinkedHashMap}, dizi -> {@link ArrayList}, tamsayi -> {@link Long}
 * (cok buyukse {@link BigInteger}), ondalik -> {@link BigDecimal}, metin, boolean, null.
 * <p>
 * Bozuk ya da nesne olmayan JSON govdesi Laravel'deki gibi bos sayilir.
 * Govde istek basina bir kez okunur ve istekte saklanir (rate limiter ve controller
 * ayni girdiyi gorur); bu yuzden bu sinifi kullanan endpoint'ler {@code @RequestBody} kullanmaz.
 */
public final class RequestInput {

	private static final String ATTRIBUTE = RequestInput.class.getName();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Map<String, Object> data;

	private RequestInput(Map<String, Object> data) {
		this.data = Collections.unmodifiableMap(data);
	}

	public static RequestInput of(HttpServletRequest request) {
		Object cached = request.getAttribute(ATTRIBUTE);
		if (cached instanceof RequestInput input) {
			return input;
		}
		Map<String, Object> merged = new LinkedHashMap<>(body(request));
		request.getParameterMap().forEach((name, values) -> {
			// Sorgu parametreleri InputNormalizationFilter'dan normallestirilmis gelir
			if (!merged.containsKey(name) && values.length > 0) {
				merged.put(name, values[0]);
			}
		});
		RequestInput input = new RequestInput(merged);
		request.setAttribute(ATTRIBUTE, input);
		return input;
	}

	/** Testler ve elle kurulan girdiler icin: degerler normallestirilir. */
	public static RequestInput of(Map<String, ?> raw) {
		Map<String, Object> data = new LinkedHashMap<>();
		raw.forEach((k, v) -> data.put(k, normalize(k, v, true)));
		return new RequestInput(data);
	}

	public Map<String, Object> all() {
		return data;
	}

	/** {@code Arr::get}: nokta yolu, yoksa null. */
	public Object get(String path) {
		return Paths.get(data, path).orElse(null);
	}

	/** {@code Arr::has}: anahtar var mi (degeri null olsa bile). */
	public boolean has(String path) {
		return Paths.get(data, path).isPresent() || Paths.exists(data, path);
	}

	// ---- govde ------------------------------------------------------------

	private static Map<String, Object> body(HttpServletRequest request) {
		String type = request.getContentType();
		if (type == null || !type.toLowerCase().contains("json")) {
			return Map.of();
		}
		JsonNode tree;
		try (InputStream in = request.getInputStream()) {
			byte[] bytes = in.readAllBytes();
			if (bytes.length == 0) {
				return Map.of();
			}
			tree = JSON.readTree(bytes);
		}
		catch (IOException | RuntimeException malformed) {
			// Laravel: json_decode basarisiz -> bos girdi
			return Map.of();
		}
		if (tree == null || !tree.isObject()) {
			return Map.of();
		}
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> e : tree.properties()) {
			out.put(e.getKey(), normalize(e.getKey(), toPhp(e.getValue()), true));
		}
		return out;
	}

	private static Object toPhp(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isObject()) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (Map.Entry<String, JsonNode> e : node.properties()) {
				map.put(e.getKey(), toPhp(e.getValue()));
			}
			return map;
		}
		if (node.isArray()) {
			List<Object> list = new ArrayList<>();
			node.forEach(n -> list.add(toPhp(n)));
			return list;
		}
		if (node.isBoolean()) {
			return node.booleanValue();
		}
		if (node.isIntegralNumber()) {
			BigInteger value = node.bigIntegerValue();
			return value.bitLength() < 64 ? (Object) value.longValue() : value;
		}
		if (node.isNumber()) {
			return node.decimalValue();
		}
		return node.asString();
	}

	/**
	 * TrimStrings (yalnizca ust duzeydeki sifre anahtarlari haric, Laravel'deki gibi)
	 * ve ConvertEmptyStringsToNull, ic ice butun degerlere.
	 */
	@SuppressWarnings("unchecked")
	private static Object normalize(String key, Object value, boolean topLevel) {
		if (value instanceof String s) {
			return LaravelStringDeserializer.normalize(s, topLevel && LaravelStringDeserializer.NOT_TRIMMED.contains(key));
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			((Map<String, Object>) map).forEach((k, v) -> out.put(k, normalize(k, v, false)));
			return out;
		}
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>();
			list.forEach(v -> out.add(normalize(null, v, false)));
			return out;
		}
		return value;
	}

	/** Nokta yolu ile ic ice erisim ({@code items.0.quantity}). */
	public static final class Paths {

		private Paths() {
		}

		/** Deger (null olabilir) yoksa empty; null deger icin {@link #exists} kullanilir. */
		static Optional<Object> get(Object root, String path) {
			Object current = root;
			for (String part : path.split("\\.", -1)) {
				current = child(current, part);
				if (current == MISSING) {
					return Optional.empty();
				}
			}
			return Optional.ofNullable(current);
		}

		public static boolean exists(Object root, String path) {
			Object current = root;
			for (String part : path.split("\\.", -1)) {
				current = child(current, part);
				if (current == MISSING) {
					return false;
				}
			}
			return true;
		}

		public static Object value(Object root, String path) {
			return get(root, path).orElse(null);
		}

		static final Object MISSING = new Object();

		static Object child(Object node, String key) {
			if (node instanceof Map<?, ?> map) {
				return map.containsKey(key) ? map.get(key) : MISSING;
			}
			if (node instanceof List<?> list) {
				try {
					int index = Integer.parseInt(key);
					return index >= 0 && index < list.size() ? list.get(index) : MISSING;
				}
				catch (NumberFormatException e) {
					return MISSING;
				}
			}
			return MISSING;
		}

	}

}
