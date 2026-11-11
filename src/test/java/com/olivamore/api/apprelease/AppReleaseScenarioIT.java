package com.olivamore.api.apprelease;

import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Uygulama surumu ve APK indirme: her senaryoda surum klasoru bastan kurulur, istekler sirayla gonderilir; durum,
 * govde (JSON ya da APK baytlari) ve basliklar (Content-Type, Content-Disposition, Range, Cache-Control, rate
 * limit) karsilastirilir. Senaryo: {@code scenarios/app-release.json}; {@code {base_url}} sunucunun adresi.
 */
@ApiIntegrationTest
class AppReleaseScenarioIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Set<String> COMPARED = Set.of("content-type", "content-disposition", "content-length",
		"accept-ranges", "content-range", "cache-control", "x-ratelimit-limit", "x-ratelimit-remaining", "retry-after");

	@LocalServerPort
	int port;

	@Value("${olive.releases-path}")
	Path releases;

	@Test
	void scenario() throws Exception {
		JsonNode scenario;
		try (InputStream in = getClass().getResourceAsStream("/scenarios/app-release.json")) {
			scenario = JSON.readTree(in);
		}
		RestClient http = RestClient.create("http://localhost:" + port);
		List<String> failures = new ArrayList<>();
		int requests = 0;

		for (JsonNode step : scenario.get("scenarios")) {
			String name = step.get("name").asString();
			writeFiles(step.get("files"));
			for (JsonNode r : step.get("requests")) {
				requests++;
				String label = name + " " + r.get("method").asString() + " " + r.get("headers");
				Response actual = call(http, r.get("method").asString(), r.get("path").asString(), r.get("headers"));

				if (actual.status != r.get("status").asInt()) {
					failures.add(label + ": durum " + actual.status + ", beklenen " + r.get("status").asInt());
					continue;
				}
				JsonNode body = r.get("body");
				if (body.has("json")) {
					String expected = JSON.writeValueAsString(body.get("json"))
						.replace("{base_url}", "http://localhost:" + port);
					if (!JSON.readTree(expected).equals(JSON.readTree(actual.body))) {
						failures.add(label + ":\n      beklenen: " + expected + "\n      gelen:    "
							+ new String(actual.body, StandardCharsets.UTF_8));
					}
				}
				else if (!body.get("base64").asString().equals(Base64.getEncoder().encodeToString(actual.body))) {
					failures.add(label + ": APK baytlari farkli");
				}
				Map<String, String> expectedHeaders = new TreeMap<>();
				r.get("response_headers").properties()
					.forEach(e -> expectedHeaders.put(e.getKey(), e.getValue().asString()));
				Map<String, String> actualHeaders = actual.headers();
				// Last-Modified: dosya zamani her calismada farkli, yalnizca varligi
				if (expectedHeaders.remove("last-modified") != null && actualHeaders.remove("last-modified") == null) {
					failures.add(label + ": Last-Modified yok");
				}
				actualHeaders.remove("last-modified");
				mask(expectedHeaders);
				mask(actualHeaders);
				if (!expectedHeaders.equals(actualHeaders)) {
					failures.add(label + ":\n      beklenen: " + expectedHeaders + "\n      gelen:    " + actualHeaders);
				}
			}
		}
		assertThat(requests).isEqualTo(32);
		assertThat(failures).as(failures.size() + " istek beklenenden farkli:\n  " + String.join("\n  ", failures))
			.isEmpty();
	}

	private static void mask(Map<String, String> headers) {
		headers.computeIfPresent("retry-after", (k, v) -> Integer.parseInt(v) >= 59 ? "~60" : v);
		// JSON cevaplarinda Content-Length tasima ayrintisi (chunked olabilir); APK'de (Range, boyut) karsilastirilir
		if (headers.getOrDefault("content-type", "").contains("json")) {
			headers.remove("content-length");
		}
	}

	/** Klasor her senaryoda bastan kurulur. */
	private void writeFiles(JsonNode files) throws IOException {
		Files.createDirectories(releases);
		try (Stream<Path> existing = Files.walk(releases)) {
			for (Path p : existing.sorted(Comparator.reverseOrder()).toList()) {
				if (!p.equals(releases)) {
					Files.delete(p);
				}
			}
		}
		if (files == null || files.isNull()) {
			return;
		}
		for (Map.Entry<String, JsonNode> f : files.properties()) {
			Files.writeString(releases.resolve(f.getKey()), f.getValue().asString(), StandardCharsets.UTF_8);
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
		RestClient.RequestBodySpec spec = http.method(HttpMethod.valueOf(method)).uri(path)
			.header("Accept", "application/json");
		headers.properties().forEach(e -> spec.header(e.getKey(), e.getValue().asString()));
		return spec.exchange((req, res) -> new Response(res.getStatusCode().value(), res.getHeaders(),
			res.getBody().readAllBytes()));
	}

}
