package com.olivamore.api.apprelease;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 32 istek (src/test/resources/laravel/app-release.json,
 * reference/capture_app_release.py) ayni dosyalarla ve ayni sirayla Java'ya oynatilir: status,
 * govde (JSON ya da APK baytlari) ve basliklar (Content-Type, Content-Disposition, Range, Cache-Control,
 * rate limit) karsilastirilir. Laravel'in AppReleaseTest'indeki 4 durum da bunlarin icinde.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppReleaseParityIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Path RELEASES;
	private static final Set<String> COMPARED = Set.of("content-type", "content-disposition", "content-length",
		"accept-ranges", "content-range", "cache-control", "x-ratelimit-limit", "x-ratelimit-remaining", "retry-after");

	static {
		try {
			RELEASES = Files.createTempDirectory("olive-releases");
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void releasesPath(DynamicPropertyRegistry registry) {
		registry.add("olive.releases-path", RELEASES::toString);
	}

	@LocalServerPort
	int port;

	@Autowired
	RateLimitBuckets rateLimiter;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		JsonNode fixture;
		try (InputStream in = getClass().getResourceAsStream("/laravel/app-release.json")) {
			fixture = JSON.readTree(in);
		}
		rateLimiter.clear();
		RestClient http = RestClient.create("http://localhost:" + port);
		List<String> failures = new ArrayList<>();
		int requests = 0;

		for (JsonNode scenario : fixture.get("scenarios")) {
			String name = scenario.get("name").asString();
			writeFiles(scenario.get("files"));
			for (JsonNode r : scenario.get("requests")) {
				requests++;
				String label = name + " " + r.get("method").asString() + " " + r.get("headers");
				Response actual = call(http, r.get("method").asString(), r.get("path").asString(), r.get("headers"));

				if (actual.status != r.get("status").asInt()) {
					failures.add(label + ": status Laravel " + r.get("status").asInt() + ", Java " + actual.status);
					continue;
				}
				JsonNode body = r.get("body");
				if (body.has("json")) {
					String expected = JSON.writeValueAsString(body.get("json"))
						.replace("http://localhost:8001", "http://localhost:" + port);
					if (!JSON.readTree(expected).equals(JSON.readTree(actual.body))) {
						failures.add(label + ":\n      laravel: " + expected + "\n      java:    " + new String(actual.body, StandardCharsets.UTF_8));
					}
				}
				else if (!body.get("base64").asString().equals(Base64.getEncoder().encodeToString(actual.body))) {
					failures.add(label + ": APK baytlari farkli");
				}
				Map<String, String> expectedHeaders = new TreeMap<>();
				r.get("response_headers").properties().forEach(e -> expectedHeaders.put(e.getKey(), e.getValue().asString()));
				Map<String, String> actualHeaders = actual.headers();
				// Last-Modified: dosya zamani farkli, yalnizca varligi
				if (expectedHeaders.remove("last-modified") != null && actualHeaders.remove("last-modified") == null) {
					failures.add(label + ": Last-Modified yok");
				}
				actualHeaders.remove("last-modified");
				mask(expectedHeaders);
				mask(actualHeaders);
				if (!expectedHeaders.equals(actualHeaders)) {
					failures.add(label + ":\n      laravel: " + expectedHeaders + "\n      java:    " + actualHeaders);
				}
			}
		}
		assertThat(requests).isEqualTo(32);
		assertThat(failures).as(failures.size() + " fark:\n  " + String.join("\n  ", failures)).isEmpty();
	}

	private static void mask(Map<String, String> headers) {
		headers.computeIfPresent("retry-after", (k, v) -> Integer.parseInt(v) >= 59 ? "~60" : v);
		// JSON cevaplarinda Content-Length tasima detayi: Laravel'in onundeki nginx JSON'u chunked gonderiyor.
		// APK indirmelerinde (Range, boyut) karsilastirilir.
		if (headers.getOrDefault("content-type", "").contains("json")) {
			headers.remove("content-length");
		}
	}

	/** capture_app_release.py: klasor her senaryoda bastan kurulur. */
	private static void writeFiles(JsonNode files) throws IOException {
		try (Stream<Path> existing = Files.walk(RELEASES)) {
			for (Path p : existing.sorted(Comparator.reverseOrder()).toList()) {
				if (!p.equals(RELEASES)) {
					Files.delete(p);
				}
			}
		}
		if (files == null || files.isNull()) {
			return;
		}
		for (Map.Entry<String, JsonNode> f : files.properties()) {
			Files.writeString(RELEASES.resolve(f.getKey()), f.getValue().asString(), StandardCharsets.UTF_8);
		}
	}

	private record Response(int status, HttpHeaders raw, byte[] body) {

		Map<String, String> headers() {
			Map<String, String> out = new TreeMap<>();
			raw.forEach((k, v) -> {
				if (COMPARED.contains(k.toLowerCase()) || k.equalsIgnoreCase("last-modified")) {
					out.put(k.toLowerCase(), String.join(", ", v));
				}
			});
			return out;
		}

	}

	private static Response call(RestClient http, String method, String path, JsonNode headers) {
		RestClient.RequestBodySpec spec = http.method(HttpMethod.valueOf(method)).uri(path).header("Accept", "application/json");
		headers.properties().forEach(e -> spec.header(e.getKey(), e.getValue().asString()));
		return spec.exchange((req, res) -> new Response(res.getStatusCode().value(), res.getHeaders(), res.getBody().readAllBytes()));
	}

}
