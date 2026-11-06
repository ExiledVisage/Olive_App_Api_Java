package com.olivamore.api.catalog;

import com.olivamore.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 77 katalog durumu (src/test/resources/laravel/catalog.json,
 * reference/capture_catalog.py) ayni sirayla Java'ya oynatilir: status ve JSON govdesi karsilastirilir.
 * <p>
 * Yollardaki ve govdelerdeki {@code {P}} gibi yer tutucular bu calismada olusan id'lerle doldurulur.
 * Laravel'deki hatalar icin Java'nin dogru davranisi {@link #DEVIATIONS}'da (DEVIATIONS.md).
 * Kendi veritabanini kullanir (veri degistirir).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=catalog-parity")
class CatalogParityIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
	private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{6}Z$");
	/** Demo verideki katalog kayitlarinin en yenisinden sonrasi bu calismada olusmustur. */
	private static final String SEEDED_UNTIL = "2026-11-06T15:36:00.000000Z";

	/** Laravel'in hatalari: Java'da beklenen status (+ varsa govde). */
	private static final Map<String, Object[]> DEVIATIONS = Map.of(
		// unique kuralinda whereHas -> 500. Java: esleme olusur (200, ->fresh()).
		"esleme ekle", new Object[] {200, null},
		"esleme mevcut global anahtar", new Object[] {200, null},
		// unique kurali olmayan kolona bakiyor -> 500. Java: anahtar bu kategoride yoksa yok sayilir.
		"esleme guncelle anahtar", new Object[] {200, null},
		// unique kurali route('processType') = null -> veritabani kisiti 500. Java: 422.
		"alan tekrar anahtar", new Object[] {422, """
			{"message":"The field key has already been taken.","errors":{"field_key":["The field key has already been taken."]}}"""});

	/**
	 * Laravel'in 500 verdigi ama sequence tukettigi durumlar (insert denendi, kisita takildi). Java bu
	 * istekleri validation'da reddettigi icin sonraki id'ler kaymasin diye ayni sequence burada tuketilir.
	 */
	private static final Map<String, String> BURNED_SEQUENCES = Map.of("alan tekrar anahtar", "process_type_fields_id_seq");

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		JsonNode fixture;
		try (InputStream in = getClass().getResourceAsStream("/laravel/catalog.json")) {
			fixture = JSON.readTree(in);
		}
		RestClient http = RestClient.create("http://localhost:" + port);
		String token = login(http);
		Map<String, Long> ids = new HashMap<>();
		List<String> failures = new ArrayList<>();
		int steps = 0;

		for (JsonNode c : fixture.get("cases")) {
			steps++;
			String name = c.get("name").asString();

			if (c.has("sql")) {
				String key = c.get("save").isNull() ? null : c.get("save").asString();
				// Laravel'in hatasini asmak icin yazilmisti; Java ayni kaydi API'den olusturduysa gerek yok
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
			if (BURNED_SEQUENCES.containsKey(name)) {
				jdbc.queryForObject("select nextval('" + BURNED_SEQUENCES.get(name) + "')", Long.class);
			}

			if (DEVIATIONS.containsKey(name)) {
				Object[] expected = DEVIATIONS.get(name);
				if (actual.status != (int) expected[0]) {
					failures.add(name + ": status " + actual.status + ", beklenen (Laravel hatasi duzeltilmis) " + expected[0] + " " + actual.body);
				}
				else if (expected[1] != null && !JSON.readTree((String) expected[1]).equals(JSON.readTree(actual.body))) {
					failures.add(name + ": govde " + actual.body);
				}
				continue;
			}

			int status = c.get("status").asInt();
			if (actual.status != status) {
				failures.add(name + ": status Laravel " + status + ", Java " + actual.status + " " + abbreviate(actual.body));
				continue;
			}
			Object expectedBody = normalize(JSON.treeToValue(c.get("response"), Object.class));
			Object actualBody = normalize(actual.body.isEmpty() ? null : JSON.readValue(actual.body, Object.class));
			if (!expectedBody.equals(actualBody)) {
				failures.add(name + ":\n      laravel: " + abbreviate(JSON.writeValueAsString(expectedBody))
					+ "\n      java:    " + abbreviate(JSON.writeValueAsString(actualBody)));
			}
		}
		assertThat(steps).isEqualTo(77);
		assertThat(failures).as(failures.size() + " fark:\n  " + String.join("\n  ", failures)).isEmpty();
	}

	/**
	 * Laravel'de UpdateProcessTypeRequest ignore() icin route('processType') kullaniyor (parametre adi
	 * process_type): bir proses tipini kendi adiyla guncellemek "already been taken" 422 veriyor. Java: 200.
	 */
	@Test
	void processTypeCanKeepItsOwnName() {
		RestClient http = RestClient.create("http://localhost:" + port);
		String token = login(http);
		String name = jdbc.queryForObject("select name from process_types where id = 1", String.class);
		Response same = call(http, "PATCH", "/api/process-types/1", token, JSON.readTree("{\"name\":\"" + name + "\"}"));
		assertThat(same.status).as(same.body).isEqualTo(200);
		String other = jdbc.queryForObject("select name from process_types where id = 2", String.class);
		Response taken = call(http, "PATCH", "/api/process-types/1", token, JSON.readTree("{\"name\":\"" + other + "\"}"));
		assertThat(taken.status).isEqualTo(422);
		assertThat(taken.body).contains("The name has already been taken.");
	}

	// ---- yer tutucular -----------------------------------------------------

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
		Matcher m = PLACEHOLDER.matcher(text);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(out, String.valueOf(ids.get(m.group(1))));
		}
		return m.appendTail(out).toString();
	}

	private static JsonNode fillBody(JsonNode body, Map<String, Long> ids) {
		String text = JSON.writeValueAsString(body);
		Matcher m = Pattern.compile("\"\\{(\\w+)}\"").matcher(text);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(out, String.valueOf(ids.get(m.group(1))));
		}
		return JSON.readTree(m.appendTail(out).toString());
	}

	// ---- normallestirme ----------------------------------------------------

	@SuppressWarnings("unchecked")
	private static Object normalize(Object value) {
		if (value == null) {
			return "<null>";
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new TreeMap<>();
			((Map<String, Object>) map).forEach((k, v) -> out.put(k, normalize(v)));
			return out;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(CatalogParityIT::normalize).toList();
		}
		if (value instanceof String s && TIMESTAMP.matcher(s).matches() && s.compareTo(SEEDED_UNTIL) > 0) {
			return "<yeni zaman>";
		}
		return value;
	}

	private static String abbreviate(String s) {
		return s.length() > 1500 ? s.substring(0, 1500) + "..." : s;
	}

	// ---- HTTP --------------------------------------------------------------

	private record Response(int status, String body) {
	}

	private static String login(RestClient http) {
		Response r = call(http, "POST", "/api/auth/login", null,
			JSON.readTree("{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}"));
		return JSON.readTree(r.body).get("token").asString();
	}

	private static Response call(RestClient http, String method, String path, String token, JsonNode body) {
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
