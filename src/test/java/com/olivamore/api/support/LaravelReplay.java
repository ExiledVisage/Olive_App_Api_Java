package com.olivamore.api.support;

import com.olivamore.api.shared.time.Timestamps;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Laravel'den yakalanan bir fixture'i (reference/capture_*.py, laravel_capture.py bicimi) ayni sirayla
 * Java'ya oynatir; status ve JSON govdesini karsilastirir, farklari toplar.
 * <ul>
 *   <li>Yollardaki ve govdedeki {@code {X}} yer tutuculari bu calismada olusan id'lerle doldurulur</li>
 *   <li>{@code sql} adimlari calistirilir; kaydettigi id zaten API'den geldiyse atlanir (Laravel'in hatasini
 *       asmak icin yazilmislardi)</li>
 *   <li>Zaman damgalari {@code seededUntil}'den yeniyse maskelenir (bu calismada olustu)</li>
 *   <li>{@link #deviation}: Laravel'in hatasi; Java'nin beklenen status (+ govde) degeri</li>
 *   <li>{@link #burn}: Laravel'in 500 verip sequence tukettigi durumda ayni sequence tuketilir (id'ler kaymasin)</li>
 * </ul>
 */
public final class LaravelReplay {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
	private static final Pattern BODY_PLACEHOLDER = Pattern.compile("\"\\{(\\w+)}\"");
	private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d+)?Z$");

	private final String fixture;
	private final int port;
	private final JdbcTemplate jdbc;
	private java.time.Instant seededUntil = java.time.Instant.parse("2026-11-06T15:36:00Z");
	private final Map<String, Object[]> deviations = new LinkedHashMap<>();
	private final Map<String, String> burned = new LinkedHashMap<>();
	private final Map<String, List<String>> unordered = new LinkedHashMap<>();
	private final List<Pattern> masks = new ArrayList<>();
	private final java.util.Set<String> unorderedKeys = new java.util.HashSet<>();
	private java.time.Instant capturedAt;

	public LaravelReplay(String fixture, int port, JdbcTemplate jdbc) {
		this.fixture = fixture;
		this.port = port;
		this.jdbc = jdbc;
	}

	public LaravelReplay seededUntil(String timestamp) {
		this.seededUntil = java.time.Instant.parse(timestamp);
		return this;
	}

	/**
	 * Fixture'in yakalandigi an: replay suresince uygulamanin saati ({@link Timestamps}) bu ana kaydirilir (akmaya
	 * devam eder). "Bugun"e bagli sonuclar (varsayilan tarih, gune gore sayim) yakalandigi gundeki gibi olur.
	 */
	public LaravelReplay capturedAt(String instant) {
		this.capturedAt = java.time.Instant.parse(instant);
		return this;
	}

	/** @param body null ise yalnizca status karsilastirilir */
	public LaravelReplay deviation(String caseName, int status, String body) {
		deviations.put(caseName, new Object[] {status, body});
		return this;
	}

	public LaravelReplay burn(String caseName, String sequence) {
		burned.put(caseName, sequence);
		return this;
	}

	public record Result(int steps, List<String> failures) {

		public String report() {
			return failures.size() + " fark:\n  " + String.join("\n  ", failures);
		}

	}

	/**
	 * Siralamasiz iliski ({@code ORDER BY} yok): satirlarin fiziksel sirasi baglantiya gore degisebilir
	 * (PHP her istekte yeni baglanti, Java havuz). Bu durumda listedeki elemanlar id'ye gore siralanip
	 * karsilastirilir. {@code path}: noktali yol, ornek {@code data.attribute_rules}.
	 */
	public LaravelReplay unordered(String caseName, String path) {
		unordered.computeIfAbsent(caseName, k -> new ArrayList<>()).add(path);
		return this;
	}

	/**
	 * ORDER BY'siz iliskiler: butun durumlarda, her derinlikte bu anahtarlardaki listeler id'ye (yoksa
	 * {@code finished_product.id}'ye) gore karsilastirilir. Guncellenen satirin fiziksel yeri degistigi icin
	 * Laravel ile Java'nin sirasi farkli olabilir (DEVIATIONS 11).
	 */
	public LaravelReplay unorderedEverywhere(String... keys) {
		unorderedKeys.addAll(List.of(keys));
		return this;
	}

	/** Rastgele degerler (ornek: transfer referansi): bu desene uyan metinler karsilastirmada esit sayilir. */
	public LaravelReplay mask(String regex) {
		masks.add(Pattern.compile(regex));
		return this;
	}

	public Result run() throws IOException {
		if (capturedAt == null) {
			return replay();
		}
		java.time.Clock system = java.time.Clock.systemUTC();
		Timestamps.useClock(java.time.Clock.offset(system, java.time.Duration.between(system.instant(), capturedAt)));
		try {
			return replay();
		}
		finally {
			Timestamps.useClock(system);
		}
	}

	private Result replay() throws IOException {
		JsonNode root;
		try (InputStream in = getClass().getResourceAsStream(fixture)) {
			root = JSON.readTree(in);
		}
		RestClient http = client(port);
		Map<String, String> tokens = new HashMap<>();
		String token = login(http, "admin@olivamore.com", tokens);
		Map<String, Long> ids = new HashMap<>();
		List<String> failures = new ArrayList<>();
		int steps = 0;

		for (JsonNode c : root.get("cases")) {
			steps++;
			String name = c.get("name").asString();
			if (c.has("sql")) {
				String key = c.get("save").isNull() ? null : c.get("save").asString();
				if (key != null && ids.containsKey(key)) {
					continue;
				}
				Long id = jdbc.queryForObject(fill(c.get("sql").asString(), ids), Long.class);
				if (key != null) {
					ids.put(key, id);
				}
				continue;
			}
			String path = fill(c.get("path").asString(), ids);
			JsonNode body = c.get("body") == null || c.get("body").isNull() ? null : fillBody(c.get("body"), ids);
			String as = c.has("as") ? c.get("as").asString() : null;
			Response actual = call(http, c.get("method").asString(), path, as == null ? token : login(http, as, tokens), body);
			save(c, actual, ids);
			if (burned.containsKey(name)) {
				jdbc.queryForObject("select nextval('" + burned.get(name) + "')", Long.class);
			}

			if (deviations.containsKey(name)) {
				Object[] expected = deviations.get(name);
				if (actual.status != (int) expected[0]) {
					failures.add(name + ": status " + actual.status + ", beklenen (Laravel hatasi duzeltilmis) " + expected[0] + " " + cut(actual.body));
				}
				else if (expected[1] != null && !JSON.readTree(fill((String) expected[1], ids)).equals(JSON.readTree(actual.body))) {
					failures.add(name + ": govde " + cut(actual.body));
				}
				continue;
			}
			int status = c.get("status").asInt();
			if (actual.status != status) {
				failures.add(name + ": status Laravel " + status + ", Java " + actual.status + " " + cut(actual.body));
				continue;
			}
			Object expected = sortLists(normalize(JSON.treeToValue(fillInstance(c.get("response"), ids), Object.class)),
				name);
			Object got = sortLists(normalize(actual.body.isEmpty() ? null : JSON.readValue(actual.body, Object.class)), name);
			if (!expected.equals(got)) {
				failures.add(name + ": " + String.join("; ", differences(expected, got, "", new ArrayList<>())));
			}
		}
		return new Result(steps, failures);
	}

	/** Kullanici basina bir kez giris (sifre seed'deki 123). */
	private static String login(RestClient http, String email, Map<String, String> tokens) {
		return tokens.computeIfAbsent(email, e -> JSON.readTree(call(http, "POST", "/api/auth/login", null,
			JSON.createObjectNode().put("email", e).put("password", "123")).body).get(TestAuth.ACCESS_TOKEN).asString());
	}

	private static void save(JsonNode c, Response actual, Map<String, Long> ids) {
		JsonNode save = c.get("save");
		if (save == null || save.isNull() || actual.status >= 300) {
			return;
		}
		JsonNode node = JSON.readTree(actual.body);
		for (String part : save.get(1).asString().split("\\.")) {
			node = node.get(part);
		}
		ids.put(save.get(0).asString(), node.asLong());
	}

	/** Hata cevabinin {@code instance}'i istegin yolu: yoldaki yer tutucular bu calismanin id'leriyle doldurulur. */
	private static JsonNode fillInstance(JsonNode response, Map<String, Long> ids) {
		if (response != null && response.isObject() && response.has("instance")) {
			tools.jackson.databind.node.ObjectNode copy = ((tools.jackson.databind.node.ObjectNode) response).deepCopy();
			copy.put("instance", fill(response.get("instance").asString(), ids));
			return copy;
		}
		return response;
	}

	private static String fill(String text, Map<String, Long> ids) {
		return replace(PLACEHOLDER.matcher(text), ids);
	}

	private static JsonNode fillBody(JsonNode body, Map<String, Long> ids) {
		return JSON.readTree(replace(BODY_PLACEHOLDER.matcher(JSON.writeValueAsString(body)), ids));
	}

	private static String replace(Matcher m, Map<String, Long> ids) {
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(out, String.valueOf(ids.get(m.group(1))));
		}
		return m.appendTail(out).toString();
	}

	@SuppressWarnings("unchecked")
	private Object normalize(Object value) {
		if (value == null) {
			return "<null>";
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new TreeMap<>();
			((Map<String, Object>) map).forEach((k, v) -> out.put(k, normalize(v)));
			return out;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(this::normalize).toList();
		}
		if (value instanceof String s && TIMESTAMP.matcher(s).matches() && java.time.Instant.parse(s).isAfter(seededUntil)) {
			return "<yeni zaman>";
		}
		if (value instanceof String s && masks.stream().anyMatch(m -> m.matcher(s).matches())) {
			return "<maske>";
		}
		// JSON'da sayilar degerce karsilastirilir: 1.0 ile 1, 12.5000 ile 12.5 esit
		if (value instanceof Number n) {
			return new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	private Object sortLists(Object value, String caseName) {
		for (String path : unordered.getOrDefault(caseName, List.of())) {
			Object node = value;
			String[] parts = path.split("\\.");
			for (int i = 0; i < parts.length - 1 && node instanceof Map<?, ?>; i++) {
				node = ((Map<String, Object>) node).get(parts[i]);
			}
			if (node instanceof Map<?, ?> map && map.get(parts[parts.length - 1]) instanceof List<?> list) {
				List<Object> sorted = new ArrayList<>(list);
				sorted.sort(java.util.Comparator.comparing(e -> e instanceof Map<?, ?> m ? new java.math.BigDecimal(String.valueOf(m.get("id"))) : java.math.BigDecimal.ZERO));
				((Map<String, Object>) map).put(parts[parts.length - 1], sorted);
			}
		}
		if (!unorderedKeys.isEmpty()) {
			sortKeyedLists(value);
		}
		return value;
	}

	/** {@link #unorderedEverywhere}: bu anahtarlardaki listeler her derinlikte id'ye gore. */
	@SuppressWarnings("unchecked")
	private void sortKeyedLists(Object value) {
		if (value instanceof Map<?, ?> map) {
			for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) {
				sortKeyedLists(e.getValue());
				if (unorderedKeys.contains(e.getKey()) && e.getValue() instanceof List<?> list) {
					List<Object> sorted = new ArrayList<>(list);
					sorted.sort(java.util.Comparator.comparing(LaravelReplay::sortKey));
					e.setValue(sorted);
				}
			}
		}
		else if (value instanceof List<?> list) {
			list.forEach(this::sortKeyedLists);
		}
	}

	/** Elemanin id'si; yoksa {@code finished_product.id} (raf icerigi gibi id'siz satirlar). */
	private static java.math.BigDecimal sortKey(Object element) {
		Object id = null;
		if (element instanceof Map<?, ?> m) {
			id = m.get("id");
			if (id == null && m.get("finished_product") instanceof Map<?, ?> product) {
				id = product.get("id");
			}
		}
		try {
			return new java.math.BigDecimal(String.valueOf(id));
		}
		catch (NumberFormatException e) {
			return java.math.BigDecimal.ZERO;
		}
	}

	/** Ilk birkac farkli yol: {@code data.processes.3.status: laravel=... java=...} */
	@SuppressWarnings("unchecked")
	private static List<String> differences(Object expected, Object got, String path, List<String> out) {
		if (out.size() >= 5 || java.util.Objects.equals(expected, got)) {
			return out;
		}
		if (expected instanceof Map<?, ?> a && got instanceof Map<?, ?> b) {
			java.util.Set<Object> keys = new java.util.TreeSet<>();
			keys.addAll(a.keySet());
			keys.addAll(b.keySet());
			for (Object k : keys) {
				Object x = a.containsKey(k) ? a.get(k) : "<yok>";
				Object y = b.containsKey(k) ? b.get(k) : "<yok>";
				differences(x, y, path.isEmpty() ? k.toString() : path + "." + k, out);
			}
			return out;
		}
		if (expected instanceof List<?> a && got instanceof List<?> b) {
			if (a.size() != b.size()) {
				out.add(path + ": eleman sayisi laravel=" + a.size() + " java=" + b.size());
			}
			for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
				differences(a.get(i), b.get(i), path + "." + i, out);
			}
			return out;
		}
		out.add(path + ": laravel=" + cut(JSON.writeValueAsString(expected), 200) + " java=" + cut(JSON.writeValueAsString(got), 200));
		return out;
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) + "..." : s;
	}

	private static String cut(String s) {
		return s.length() > 1500 ? s.substring(0, 1500) + "..." : s;
	}

	/** Fixture yollari zaten URL-kodlu (Python yakaladi): tekrar kodlanmasin ("%C3" -> "%25C3" olmasin). */
	public static RestClient client(int port) {
		org.springframework.web.util.DefaultUriBuilderFactory uris = new org.springframework.web.util.DefaultUriBuilderFactory("http://localhost:" + port);
		uris.setEncodingMode(org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode.NONE);
		return RestClient.builder().uriBuilderFactory(uris).build();
	}

	public record Response(int status, String body) {
	}

	public static Response call(RestClient http, String method, String path, String token, JsonNode body) {
		RestClient.RequestBodySpec spec = http.method(HttpMethod.valueOf(method)).uri(path).accept(MediaType.APPLICATION_JSON);
		if (token != null) {
			spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			spec.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
		}
		return spec.exchange((req, res) -> new Response(res.getStatusCode().value(), new String(res.getBody().readAllBytes())));
	}

}
