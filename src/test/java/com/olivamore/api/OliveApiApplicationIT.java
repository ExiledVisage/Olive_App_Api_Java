package com.olivamore.api;

import com.olivamore.api.support.ApiClient;
import com.olivamore.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uygulamanin tamami gercek Tomcat'te: filtreler, Modulith insight proxy'leri ve hata
 * yonetimi birlikte. MOCK web ortaminda filtreler baslatilmadigi icin bazi hatalar
 * (orn. proxy'lenmis filtrenin init() hatasi) ancak burada gorunur.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

	private Response get(String path) {
		return get(path, null);
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
