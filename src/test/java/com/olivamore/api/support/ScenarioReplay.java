package com.olivamore.api.support;

import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.support.ApiClient.Response;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API senaryosu ({@code src/test/resources/scenarios/*.json}): adimlar sirayla calistirilir, her adimin durumu ve
 * JSON govdesi beklenenle karsilastirilir.
 * <ul>
 *   <li>Adim alanlari: {@code name}, {@code method}, {@code path}, {@code body}, {@code status}, {@code response};
 *       {@code save: [anahtar, yol]} cevaptaki bir id'yi sonraki adimlar icin saklar</li>
 *   <li>Kimlik: {@code as} (e-posta, verilmezse admin), {@code anonymous: true} (tokensiz), {@code token}
 *       (sabit bearer degeri); {@code headers} verilmisse rate limit basliklari da karsilastirilir</li>
 *   <li>{@code sql} adimlari hazirlik icindir: sonucu (id) {@code save} anahtarina yazilir</li>
 *   <li>Yollardaki ve govdedeki {@code {X}} yer tutuculari saklanan id'lerle doldurulur</li>
 *   <li>Demo veriden ({@link #seededUntil}) yeni zaman damgalari ve maskeler ({@link #mask}) karsilastirmada
 *       sabitlenir; listeler sirasiyla karsilastirilir</li>
 * </ul>
 * {@code -Dscenario.record=<klasor>} verilirse tutmayan adimlar gelen cevapla, atlanan hazirlik adimlari
 * cikarilarak senaryonun yeni hali o klasore yazilir (bilincli bir sozlesme degisikliginden sonra senaryoyu
 * guncellemek icin); bu kipte test basarisiz sayilmaz.
 */
public final class ScenarioReplay {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
	private static final Pattern BODY_PLACEHOLDER = Pattern.compile("\"\\{(\\w+)}\"");
	private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d+)?Z$");
	private static final String RECORD = System.getProperty("scenario.record", "");

	private final String scenario;
	private final int port;
	private final JdbcTemplate jdbc;
	private Instant seededUntil = Instant.parse("2026-11-06T15:36:00Z");
	private Instant clockAt;
	private final List<String> firstLogins = new ArrayList<>();
	private final List<Pattern> masks = new ArrayList<>();

	/** @param scenario {@code src/test/resources/scenarios} altindaki dosya adi, ornek {@code storage-unit.json} */
	public ScenarioReplay(String scenario, int port, JdbcTemplate jdbc) {
		this.scenario = scenario;
		this.port = port;
		this.jdbc = jdbc;
	}

	/** Demo verideki en yeni zaman: bundan yeni damgalar bu calismada olustugu icin karsilastirilmaz. */
	public ScenarioReplay seededUntil(String timestamp) {
		this.seededUntil = Instant.parse(timestamp);
		return this;
	}

	/**
	 * Senaryo suresince uygulamanin saati ({@link Timestamps}) bu ana kaydirilir (akmaya devam eder): "bugun"e
	 * bagli sonuclar (varsayilan tarih, gune gore sayim) senaryonun yazildigi gundeki gibi olur.
	 */
	public ScenarioReplay clockAt(String instant) {
		this.clockAt = Instant.parse(instant);
		return this;
	}

	/** Senaryo baslamadan bu sirayla giris yapilir (giris limiti sayaclari senaryodaki gibi baslasin). */
	public ScenarioReplay loginFirst(String... emails) {
		firstLogins.addAll(List.of(emails));
		return this;
	}

	/** Rastgele degerler (transfer referansi, token): bu desene uyan metinler esit sayilir. */
	public ScenarioReplay mask(String regex) {
		masks.add(Pattern.compile(regex));
		return this;
	}

	public record Result(String scenario, int steps, List<String> failures, boolean recorded) {

		/** Adim sayisi ve butun adimlarin tutmasi; kayit kipinde yalnizca rapor yazilir. */
		public void assertPassed(int expectedSteps) {
			if (recorded) {
				return;
			}
			assertThat(steps).as(scenario + " adim sayisi").isEqualTo(expectedSteps);
			assertThat(failures).as(failures.size() + " adim beklenenden farkli:\n  " + String.join("\n  ", failures))
				.isEmpty();
		}

	}

	public Result run() throws IOException {
		if (clockAt == null) {
			return replay();
		}
		Clock system = Clock.systemUTC();
		Timestamps.useClock(Clock.offset(system, Duration.between(system.instant(), clockAt)));
		try {
			return replay();
		}
		finally {
			Timestamps.useClock(system);
		}
	}

	private Result replay() throws IOException {
		ObjectNode root;
		try (InputStream in = getClass().getResourceAsStream("/scenarios/" + scenario)) {
			root = (ObjectNode) JSON.readTree(Objects.requireNonNull(in, scenario));
		}
		RestClient http = ApiClient.http(port);
		Map<String, String> tokens = new HashMap<>();
		firstLogins.forEach(email -> login(http, email, tokens));
		String admin = login(http, Api.ADMIN, tokens);
		Map<String, Long> ids = new HashMap<>();
		List<String> failures = new ArrayList<>();
		ArrayNode recorded = JSON.createArrayNode();
		int steps = 0;

		for (JsonNode step : root.get("steps")) {
			steps++;
			String name = step.get("name").asString();
			if (step.has("sql")) {
				String key = step.get("save").isNull() ? null : step.get("save").asString();
				if (key != null && ids.containsKey(key)) {
					System.out.println("[senaryo " + scenario + "] atlanan hazirlik adimi: " + name);
					continue;
				}
				Long id = jdbc.queryForObject(fill(step.get("sql").asString(), ids), Long.class);
				if (key != null) {
					ids.put(key, id);
				}
				recorded.add(step);
				continue;
			}
			String path = fill(step.get("path").asString(), ids);
			JsonNode body = step.get("body") == null || step.get("body").isNull() ? null : fillBody(step.get("body"), ids);
			Response actual = ApiClient.call(http, step.get("method").asString(), path, token(step, http, tokens, admin),
				body);
			save(step, actual, ids);

			String failure = compare(step, actual, ids);
			if (failure == null) {
				recorded.add(step);
				continue;
			}
			failures.add(name + ": " + failure);
			ObjectNode updated = ((ObjectNode) step).deepCopy();
			updated.put("status", actual.status());
			updated.set("response", actual.body().isEmpty() ? JSON.nullNode() : JSON.readTree(actual.body()));
			if (step.has("headers")) {
				updated.set("headers", JSON.valueToTree(rateLimitHeaders(actual)));
			}
			recorded.add(updated);
		}
		if (!RECORD.isEmpty()) {
			root.set("steps", recorded);
			Path out = Path.of(RECORD).resolve(scenario);
			Files.createDirectories(out.getParent());
			Files.writeString(out, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
			System.out.println("[senaryo " + scenario + "] " + failures.size() + " adim guncellendi -> " + out
				+ "\n  " + String.join("\n  ", failures));
		}
		return new Result(scenario, steps, failures, !RECORD.isEmpty());
	}

	/** Tutmuyorsa nedeni, tutuyorsa null. */
	private String compare(JsonNode step, Response actual, Map<String, Long> ids) {
		int status = step.get("status").asInt();
		if (actual.status() != status) {
			return "durum " + actual.status() + ", beklenen " + status + " " + cut(actual.body(), 1500);
		}
		Object expected = normalize(JSON.treeToValue(fillInstance(step.get("response"), ids), Object.class));
		Object got = normalize(actual.body().isEmpty() ? null : JSON.readValue(actual.body(), Object.class));
		if (!expected.equals(got)) {
			return String.join("; ", differences(expected, got, "", new ArrayList<>()));
		}
		if (step.has("headers")) {
			Map<String, String> expectedHeaders = new TreeMap<>();
			step.get("headers").properties()
				.forEach(e -> expectedHeaders.put(e.getKey().toLowerCase(), e.getValue().asString()));
			Map<String, String> actualHeaders = rateLimitHeaders(actual);
			if (!maskHeaders(expectedHeaders).equals(maskHeaders(actualHeaders))) {
				return "basliklar " + actualHeaders + ", beklenen " + expectedHeaders;
			}
		}
		return null;
	}

	private static Map<String, String> rateLimitHeaders(Response actual) {
		Map<String, String> headers = new TreeMap<>();
		actual.headers().forEach((k, v) -> {
			String key = k.toLowerCase();
			if (key.startsWith("x-ratelimit") || key.equals("retry-after")) {
				headers.put(key, v.getFirst());
			}
		});
		return headers;
	}

	private static Map<String, String> maskHeaders(Map<String, String> headers) {
		headers.computeIfPresent("x-ratelimit-reset", (k, v) -> "<zaman>");
		// Retry-After: pencere ayni saniyede ya da bir saniye once acilmis olabilir
		headers.computeIfPresent("retry-after", (k, v) -> Integer.parseInt(v) >= 59 ? "~60" : v);
		return headers;
	}

	private static String token(JsonNode step, RestClient http, Map<String, String> tokens, String admin) {
		if (step.path("anonymous").asBoolean(false)) {
			return null;
		}
		if (step.has("token")) {
			return step.get("token").asString();
		}
		return step.has("as") && !step.get("as").isNull() ? login(http, step.get("as").asString(), tokens) : admin;
	}

	/** Kullanici basina bir kez giris. */
	private static String login(RestClient http, String email, Map<String, String> tokens) {
		return tokens.computeIfAbsent(email, e -> TestAuth.accessToken(http, e));
	}

	private static void save(JsonNode step, Response actual, Map<String, Long> ids) {
		JsonNode save = step.get("save");
		if (save == null || save.isNull() || actual.status() >= 300) {
			return;
		}
		JsonNode node = JSON.readTree(actual.body());
		for (String part : save.get(1).asString().split("\\.")) {
			node = node.get(part);
		}
		ids.put(save.get(0).asString(), node.asLong());
	}

	/** Hata cevabinin {@code instance}'i istegin yolu: yoldaki yer tutucular saklanan id'lerle doldurulur. */
	private static JsonNode fillInstance(JsonNode response, Map<String, Long> ids) {
		if (response != null && response.isObject() && response.has("instance")) {
			ObjectNode copy = ((ObjectNode) response).deepCopy();
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
		if (value instanceof String s && TIMESTAMP.matcher(s).matches() && Instant.parse(s).isAfter(seededUntil)) {
			return "<yeni zaman>";
		}
		if (value instanceof String s && masks.stream().anyMatch(m -> m.matcher(s).matches())) {
			return "<maske>";
		}
		// sayilar degerce karsilastirilir: 1.0 ile 1, 12.5000 ile 12.5 esit
		if (value instanceof Number n) {
			return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
		}
		return value;
	}

	/** Ilk birkac farkli yol: {@code data.processes.3.status: beklenen=... gelen=...} */
	@SuppressWarnings("unchecked")
	private static List<String> differences(Object expected, Object got, String path, List<String> out) {
		if (out.size() >= 5 || Objects.equals(expected, got)) {
			return out;
		}
		if (expected instanceof Map<?, ?> a && got instanceof Map<?, ?> b) {
			Set<Object> keys = new TreeSet<>();
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
				out.add(path + ": eleman sayisi beklenen=" + a.size() + " gelen=" + b.size());
			}
			for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
				differences(a.get(i), b.get(i), path + "." + i, out);
			}
			return out;
		}
		out.add(path + ": beklenen=" + cut(JSON.writeValueAsString(expected), 200) + " gelen="
			+ cut(JSON.writeValueAsString(got), 200));
		return out;
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) + "..." : s;
	}

}
