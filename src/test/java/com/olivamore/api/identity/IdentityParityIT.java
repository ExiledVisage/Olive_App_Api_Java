package com.olivamore.api.identity;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 56 durum (src/test/resources/laravel/identity.json, reference/capture_identity.py)
 * ayni sirayla Java'ya tekrar oynatilir; status, JSON govdesi ve rate limit basliklari karsilastirilir.
 * <p>
 * Iki taraf ayni demo veriden basladigi icin id'ler de ayni cikar (yeni kullanici 8, 9; roller 7, 8).
 * Maskelenenler: token'lar, bu calismada olusan kayitlarin zaman damgalari,
 * {@code X-RateLimit-Reset}. Laravel'in hatalari icin Java'nin dogru davranisi {@link #EXPECTED_DEVIATIONS}'da.
 * <p>
 * Kendi veritabanini kullanir (ayri context): veriyi degistirir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=identity-parity")
class IdentityParityIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern JWT = Pattern.compile("^[\\w-]+\\.[\\w-]+\\.[\\w-]+$");
	private static final Pattern REFRESH_TOKEN = Pattern.compile("^[\\w-]{43}$");
	private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{6}Z$");
	/** Demo verideki en yeni kullanici zamani; bundan sonrasi bu calismada olusmustur. */
	private static final String SEEDED_UNTIL = "2026-11-06T15:35:11.000000Z";

	/** Durum adi -> istekte kullanilan sabit token (capture_identity.py ile ayni). */
	private static final Map<String, String> LITERAL_TOKENS = Map.of("bozuk token", "abc", "id'li bozuk token", "1|yanlis");

	/**
	 * Laravel'in hatalari, Java'da duzeltildi (DEVIATIONS.md). Deger: beklenen status + govde
	 * (null govde = yalnizca status). Basliklar bu durumlarda karsilastirilmaz.
	 */
	private static final Map<String, Object[]> EXPECTED_DEVIATIONS = Map.of(
		// Laravel 500: rate limiter diziyi metne ceviremiyor
		"login email dizi", new Object[] {422, """
			{"message":"The email field must be a string.",
			 "errors":{"email":["The email field must be a string."]}}"""},
		// Laravel 500: rol sanctum guard'iyla olusuyor, web izinleri atanamiyor
		"roles store izinli (Laravel 500)", new Object[] {201, null},
		// Laravel 500: sanctum guard'li rol silinemiyor
		"roles delete", new Object[] {200, "{\"message\":\"Role deleted successfully.\"}"});

	/**
	 * Henuz tasinmamis endpoint'lere giden durumlar: modul tasininca buradan silinir.
	 * Izin kontrolunun kendisi IdentityAccessIT'de (ProbeController) test ediliyor.
	 */
	private static final Map<String, String> NOT_YET_PORTED = Map.of(
		"operator izin gerektiren uc", "Faz 9: /api/customers",
		"tokensiz izin gerektiren uc", "Faz 9: /api/customers");

	@LocalServerPort
	int port;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		JsonNode fixture;
		try (InputStream in = getClass().getResourceAsStream("/laravel/identity.json")) {
			fixture = JSON.readTree(in);
		}
		RestClient http = RestClient.create("http://localhost:" + port);

		// capture_identity.py ile ayni baslangic: once admin, sonra operator1 giris yapar
		Map<String, String> tokens = new LinkedHashMap<>();
		tokens.put("admin", login(http, "admin@olivamore.com"));
		tokens.put("operator1", login(http, "operator1@olivamore.com"));

		List<String> failures = new ArrayList<>();
		int compared = 0;
		int skipped = 0;
		for (JsonNode c : fixture.get("cases")) {
			String name = c.get("name").asString();
			if (NOT_YET_PORTED.containsKey(name)) {
				skipped++;
				continue;
			}
			String who = c.get("as").isNull() ? null : c.get("as").asString();
			String token = who != null ? tokens.get(who) : LITERAL_TOKENS.get(name);
			Response actual = call(http, c.get("method").asString(), c.get("path").asString(), token, c.get("body"));
			compared++;

			if (EXPECTED_DEVIATIONS.containsKey(name)) {
				Object[] expected = EXPECTED_DEVIATIONS.get(name);
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
				failures.add(name + ": status Laravel " + status + ", Java " + actual.status + " " + actual.body);
				continue;
			}
			Object expectedBody = normalize(JSON.treeToValue(c.get("response"), Object.class));
			Object actualBody = normalize(actual.body.isEmpty() ? null : JSON.readValue(actual.body, Object.class));
			if (!expectedBody.equals(actualBody)) {
				failures.add(name + ":\n      laravel: " + JSON.writeValueAsString(expectedBody) + "\n      java:    "
					+ JSON.writeValueAsString(actualBody));
			}
			Map<String, String> expectedHeaders = headers(c.get("headers"));
			Map<String, String> actualHeaders = actual.rateLimitHeaders();
			if (!expectedHeaders.equals(actualHeaders)) {
				failures.add(name + ": basliklar Laravel " + expectedHeaders + ", Java " + actualHeaders);
			}
		}
		assertThat(compared + skipped).isEqualTo(56);
		assertThat(skipped).isEqualTo(NOT_YET_PORTED.size());
		assertThat(failures).as(failures.size() + " fark:\n  " + String.join("\n  ", failures)).isEmpty();
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
			return list.stream().map(IdentityParityIT::normalize).toList();
		}
		if (value instanceof String s) {
			if (JWT.matcher(s).matches()) {
				return "<jwt>";
			}
			if (REFRESH_TOKEN.matcher(s).matches()) {
				return "<refresh>";
			}
			if (TIMESTAMP.matcher(s).matches() && s.compareTo(SEEDED_UNTIL) > 0) {
				return "<yeni zaman>";
			}
		}
		return value;
	}

	private static Map<String, String> headers(JsonNode node) {
		Map<String, String> out = new TreeMap<>();
		node.properties().forEach(e -> out.put(e.getKey().toLowerCase(), e.getValue().asString()));
		return mask(out);
	}

	private static Map<String, String> mask(Map<String, String> headers) {
		headers.computeIfPresent("x-ratelimit-reset", (k, v) -> "<zaman>");
		// Retry-After: pencere ayni saniyede ya da bir saniye once acilmis olabilir
		headers.computeIfPresent("retry-after", (k, v) -> Integer.parseInt(v) >= 59 ? "~60" : v);
		return headers;
	}

	// ---- HTTP --------------------------------------------------------------

	private record Response(int status, HttpHeaders headers, String body) {

		Map<String, String> rateLimitHeaders() {
			Map<String, String> out = new TreeMap<>();
			headers.forEach((k, v) -> {
				String key = k.toLowerCase();
				if (key.startsWith("x-ratelimit") || key.equals("retry-after")) {
					out.put(key, v.getFirst());
				}
			});
			return mask(out);
		}

	}

	private static String login(RestClient http, String email) throws Exception {
		Response r = call(http, "POST", "/api/auth/login", null, JSON.readTree("{\"email\":\"" + email + "\",\"password\":\"123\"}"));
		assertThat(r.status).as(r.body).isEqualTo(200);
		return JSON.readTree(r.body).get(TestAuth.ACCESS_TOKEN).asString();
	}

	private static Response call(RestClient http, String method, String path, String token, JsonNode body) {
		RestClient.RequestBodySpec spec = http.method(HttpMethod.valueOf(method)).uri(path).accept(MediaType.APPLICATION_JSON);
		if (token != null) {
			spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null && !body.isNull()) {
			spec.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
		}
		return spec.exchange((request, response) -> new Response(response.getStatusCode().value(), response.getHeaders(),
			new String(response.getBody().readAllBytes())));
	}

}
