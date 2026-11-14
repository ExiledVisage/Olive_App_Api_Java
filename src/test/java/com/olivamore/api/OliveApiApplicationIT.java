package com.olivamore.api;

import com.olivamore.api.support.ApiClient;
import com.olivamore.api.support.FreshDatabaseListener;
import com.olivamore.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uygulamanin tamami gercek Tomcat'te, gelistirme profiliyle: filtreler, Modulith insight proxy'leri, hata
 * yonetimi ve API dokumantasyonu birlikte. MOCK web ortaminda filtreler baslatilmadigi icin bazi hatalar
 * (orn. proxy'lenmis filtrenin init() hatasi) ancak burada gorunur.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev"})
@TestExecutionListeners(listeners = FreshDatabaseListener.class,
	mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
// Spring Boot testlerde metrik disa aktarimini kapatir; Prometheus endpoint'i icin aciliyor
@AutoConfigureMetrics
class OliveApiApplicationIT {

	@LocalServerPort
	int port;

	@Test
	void startsAndServesHealth() {
		Response health = get("/actuator/health");
		assertThat(health.status()).isEqualTo(200);
		assertThat(health.body()).contains("\"status\":\"UP\"");
	}

	@Test
	void unknownRouteIsProblemDetail() {
		String token = TestAuth.accessToken(ApiClient.http(port), "admin@olivamore.com");
		Response response = get("/api/yok-boyle-bir-yer?search=%20a%20", token);
		assertThat(response.status()).isEqualTo(404);
		assertThat(response.body()).contains("\"detail\":\"The route api/yok-boyle-bir-yer could not be found.\"",
			"\"instance\":\"/api/yok-boyle-bir-yer\"");
	}

	@Test
	void exposesPrometheusMetrics() {
		get("/actuator/health");
		Response metrics = get("/actuator/prometheus");
		assertThat(metrics.status()).isEqualTo(200);
		assertThat(metrics.body()).contains("http_server_requests_seconds");
	}

	/** Grafana dashboard'undaki her sorgunun metrigi Prometheus ciktisinda var (is kurali reddi dahil). */
	@Test
	void dashboardQueriesUseExportedMetrics() throws IOException {
		String token = TestAuth.accessToken(ApiClient.http(port), "admin@olivamore.com");
		// tank 1 son tank: Bosalt is kuralina takilir
		Response rejected = post("/api/storage-units/1/empty",
			"{\"targets\": [{\"storage_unit_id\": 2, \"quantity\": 1}]}", token);
		assertThat(rejected.status()).as(rejected.body()).isEqualTo(422);
		String exported = get("/actuator/prometheus").body();

		JsonNode dashboard = JsonMapper.builder().build()
			.readTree(Files.readString(Path.of("docker/grafana/dashboards/olive-api.json")));
		Set<String> metrics = new TreeSet<>();
		Matcher names = Pattern.compile("([a-z_]+)\\{").matcher(dashboard.toString());
		while (names.find()) {
			metrics.add(names.group(1));
		}
		assertThat(metrics).contains("olive_business_rejections_total", "olive_tank_emptied_total",
			"hikaricp_connections_active");
		assertThat(metrics).allSatisfy(metric -> assertThat(exported).as(metric)
			.containsPattern("(?m)^" + metric + "[{ ]"));
		assertThat(exported)
			.contains("olive_business_rejections_total{method=\"POST\",uri=\"/api/storage-units/{id}/empty\"}");
	}

	@Test
	void devProfileServesApiDocsWithBearerAuth() {
		Response docs = get("/v3/api-docs");
		assertThat(docs.status()).isEqualTo(200);
		JsonNode api = JsonMapper.builder().build().readTree(docs.body());
		assertThat(api.get("openapi").asString()).startsWith("3.");
		assertThat(api.get("info").get("title").asString()).isEqualTo("Olive API");
		assertThat(api.get("components").get("securitySchemes").get("bearer-jwt").get("scheme").asString())
			.isEqualTo("bearer");
		assertThat(api.get("paths").has("/api/storage-units/{id}/empty")).isTrue();
		assertThat(api.get("paths").properties()).allSatisfy(path -> assertThat(path.getKey()).startsWith("/api/"));

		assertThat(get("/swagger-ui/index.html").status()).isEqualTo(200);
	}

	private Response get(String path) {
		return get(path, null);
	}

	private Response post(String path, String json, String token) {
		return RestClient.create("http://localhost:" + port).post().uri(path).headers(h -> h.setBearerAuth(token))
			.contentType(MediaType.APPLICATION_JSON).body(json)
			.exchange((request, response) -> new Response(response.getStatusCode(),
				new String(response.getBody().readAllBytes())));
	}

	private Response get(String path, String token) {
		return RestClient.create("http://localhost:" + port).get().uri(path)
			.headers(headers -> {
				if (token != null) {
					headers.setBearerAuth(token);
				}
			})
			.exchange((request, response) -> new Response(response.getStatusCode(),
				new String(response.getBody().readAllBytes())));
	}

	private record Response(HttpStatusCode code, String body) {
		int status() {
			return code.value();
		}
	}

}
