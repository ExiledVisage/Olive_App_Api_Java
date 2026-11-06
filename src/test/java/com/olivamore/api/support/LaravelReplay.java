package com.olivamore.api.support;

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
	/** Sayfalama linklerindeki sunucu adresi (Laravel localhost:8001, Java rastgele port). */
	private static final Pattern BASE_URL = Pattern.compile("^http://localhost:\\d+/");
	private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{6}Z$");

	private final String fixture;
	private final int port;
	private final JdbcTemplate jdbc;
	private String seededUntil = "2026-11-06T15:36:00.000000Z";
	private final Map<String, Object[]> deviations = new LinkedHashMap<>();
	private final Map<String, String> burned = new LinkedHashMap<>();
	private final Map<String, List<String>> unordered = new LinkedHashMap<>();

	public LaravelReplay(String fixture, int port, JdbcTemplate jdbc) {
		this.fixture = fixture;
		this.port = port;
		this.jdbc = jdbc;
	}

	public LaravelReplay seededUntil(String timestamp) {
		this.seededUntil = timestamp;
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

	public Result run() throws IOException {
		JsonNode root;
		try (InputStream in = getClass().getResourceAsStream(fixture)) {
			root = JSON.readTree(in);
		}
		RestClient http = client(port);
		String token = JSON.readTree(call(http, "POST", "/api/auth/login", null,
			JSON.readTree("{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}")).body).get("token").asString();
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
			Response actual = call(http, c.get("method").asString(), path, token, body);
			save(c, actual, ids);
			if (burned.containsKey(name)) {
				jdbc.queryForObject("select nextval('" + burned.get(name) + "')", Long.class);
			}

			if (deviations.containsKey(name)) {
				Object[] expected = deviations.get(name);
				if (actual.status != (int) expected[0]) {
					failures.add(name + ": status " + actual.status + ", beklenen (Laravel hatasi duzeltilmis) " + expected[0] + " " + cut(actual.body));
				}
				else if (expected[1] != null && !JSON.readTree((String) expected[1]).equals(JSON.readTree(actual.body))) {
					failures.add(name + ": govde " + cut(actual.body));
				}
				continue;
			}
			int status = c.get("status").asInt();
			if (actual.status != status) {
				failures.add(name + ": status Laravel " + status + ", Java " + actual.status + " " + cut(actual.body));
				continue;
			}
			Object expected = sortLists(normalize(JSON.treeToValue(c.get("response"), Object.class)), name);
			Object got = sortLists(normalize(actual.body.isEmpty() ? null : JSON.readValue(actual.body, Object.class)), name);
			if (!expected.equals(got)) {
				failures.add(name + ":\n      laravel: " + cut(JSON.writeValueAsString(expected)) + "\n      java:    " + cut(JSON.writeValueAsString(got)));
			}
		}
		return new Result(steps, failures);
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
		if (value instanceof String s && TIMESTAMP.matcher(s).matches() && s.compareTo(seededUntil) > 0) {
			return "<yeni zaman>";
		}
		if (value instanceof String s && BASE_URL.matcher(s).find()) {
			return BASE_URL.matcher(s).replaceFirst("<sunucu>/");
		}
		// PHP json_encode 1.0'i 1 yazar; JSON'da sayilar degerce karsilastirilir
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
		return value;
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
