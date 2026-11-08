package com.olivamore.api;

import com.olivamore.api.shared.web.throttle.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** PHP kaynagiyla satir satir karsilastirmada bulunan Java hatalari: Laravel'in davranisi burada sabit. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=parity-fixes")
class LaravelParityFixesIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	@Autowired
	RateLimiter rateLimiter;

	@Autowired
	JdbcTemplate jdbc;

	RestClient http;

	@BeforeEach
	void setUp() {
		http = RestClient.create("http://localhost:" + port);
		rateLimiter.clear();
	}

	@Test
	void numericLookingTextIsNotLostOnUpdate() {
		long id = jdbc.queryForObject("select min(id) from suppliers", Long.class);
		jdbc.update("update suppliers set phone = '5321234567' where id = ?", id);

		Response r = send(HttpMethod.PATCH, "/api/suppliers/" + id, admin(), Map.of("phone", "05321234567"));

		assertThat(r.status).as(r.body).isEqualTo(200);
		assertThat(jdbc.queryForObject("select phone from suppliers where id = ?", String.class, id)).isEqualTo("05321234567");
	}

	@Test
	void forwardedForHeaderDoesNotOpenANewLoginBucket() {
		for (int i = 1; i <= 5; i++) {
			assertThat(login("kullanici@olivamore.com", "yanlis", "203.0.113." + i).status).isEqualTo(422);
		}
		assertThat(login("kullanici@olivamore.com", "yanlis", "203.0.113.99").status).isEqualTo(429);
	}

	@Test
	void existsOnNullIsAValidationError() {
		long id = jdbc.queryForObject("select min(id) from production_product_types", Long.class);
		Map<String, Object> body = new HashMap<>();
		body.put("production_product_id", null);

		Response r = send(HttpMethod.PATCH, "/api/production-product-types/" + id, admin(), body);

		assertThat(r.status).as(r.body).isEqualTo(422);
		assertThat(r.json().get("errors").get("production_product_id").get(0).asString())
			.isEqualTo("The selected production product id is invalid.");
	}

	@Test
	void recipeWithNullContentsIsAValidationError() {
		Map<String, Object> body = new HashMap<>();
		body.put("name", "Bos tarif");
		body.put("contents", null);

		Response r = send(HttpMethod.POST, "/api/finished-product-recipes", admin(), body);

		assertThat(r.status).as(r.body).isEqualTo(422);
		assertThat(r.json().get("errors").get("contents").toString())
			.contains("The contents field is required.", "Mevcut toplam: 0");
	}

	@Test
	void rolesSentAsAJsonObjectUseTheValues() {
		Response r = send(HttpMethod.POST, "/api/roles", admin(),
			Map.of("name", "nesne-rol", "permissions", Map.of("a", "customer.view")));

		assertThat(r.status).as(r.body).isEqualTo(201);
		assertThat(r.body).contains("customer.view");
	}

	@Test
	void passwordOverSeventyTwoBytesWorks() {
		String password = "a".repeat(100);
		Response register = send(HttpMethod.POST, "/api/auth/register", null, Map.of("name", "Uzun Sifre",
			"email", "uzun@olivamore.com", "password", password, "password_confirmation", password));

		assertThat(register.status).as(register.body).isEqualTo(201);
		assertThat(login("uzun@olivamore.com", password, null).status).isEqualTo(200);
	}

	private String admin() {
		Response r = login("admin@olivamore.com", "123", null);
		assertThat(r.status).as(r.body).isEqualTo(200);
		return r.json().get("token").asString();
	}

	private Response login(String email, String password, String forwardedFor) {
		return exchange(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", password), forwardedFor);
	}

	private Response send(HttpMethod method, String path, String token, Map<String, ?> body) {
		return exchange(method, path, token, body, null);
	}

	private Response exchange(HttpMethod method, String path, String token, Map<String, ?> body, String forwardedFor) {
		return http.method(method).uri(path)
			.accept(MediaType.APPLICATION_JSON)
			.contentType(MediaType.APPLICATION_JSON)
			.headers(h -> {
				if (token != null) {
					h.setBearerAuth(token);
				}
				if (forwardedFor != null) {
					h.set("X-Forwarded-For", forwardedFor);
				}
			})
			.body(JSON.writeValueAsString(body))
			.exchange((request, response) -> new Response(response.getStatusCode().value(),
				new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private record Response(int status, String body) {
		JsonNode json() {
			return JSON.readTree(body);
		}
	}

}
