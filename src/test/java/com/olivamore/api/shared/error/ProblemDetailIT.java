package com.olivamore.api.shared.error;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Hata cevabi butun katmanlarda (dogrulama, is kurali, 404, guvenlik) ayni RFC 9457 bicimi. */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProblemDetailIT {

	@LocalServerPort
	int port;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void validationErrorListsEveryFieldWithItsMessages() {
		Response r = api.post("/api/storage-units", Map.of("name", "X", "capacity", 0));

		JsonNode body = problem(r, 422, "Unprocessable Content", "/api/storage-units");
		assertThat(body.get("detail").asString()).isEqualTo("The code field is required. (and 2 more errors)");
		assertThat(body.get("errors").properties()).extracting(Map.Entry::getKey)
			.containsExactlyInAnyOrder("code", "capacity", "capacity_unit");
		assertThat(body.get("errors").get("capacity").get(0).asString())
			.isEqualTo("The capacity field must be greater than 0.");
	}

	@Test
	void businessRuleViolationHasADetailAndNoFieldErrors() {
		Response r = api.delete("/api/storage-units/13");

		JsonNode body = problem(r, 422, "Unprocessable Content", "/api/storage-units/13");
		assertThat(body.get("detail").asString()).startsWith("'DOLUM-AYV-01' tankının");
		assertThat(body.has("errors")).isFalse();
	}

	@Test
	void missingResourceNamesTheResourceAndItsId() {
		JsonNode body = problem(api.get("/api/orders/999999"), 404, "Not Found", "/api/orders/999999");

		assertThat(body.get("detail").asString()).isEqualTo("Order 999999 not found.");
	}

	@Test
	void securityErrorsUseTheSameFormat() {
		problem(api.as(null).get("/api/orders"), 401, "Unauthorized", "/api/orders");
		// govdesiz istek: gecersiz govdede dogrulama yetkiden once calisir (422)
		problem(api.as("operator1@olivamore.com").delete("/api/orders/999999"), 403, "Forbidden", "/api/orders/999999");
	}

	private static JsonNode problem(Response r, int status, String title, String instance) {
		assertThat(r.status()).as(r.body()).isEqualTo(status);
		assertThat(MediaType.parseMediaType(r.headers().getFirst("Content-Type")).isCompatibleWith(
			MediaType.APPLICATION_PROBLEM_JSON)).as(r.headers().getFirst("Content-Type")).isTrue();
		JsonNode body = r.json();
		assertThat(body.get("status").asInt()).isEqualTo(status);
		assertThat(body.get("title").asString()).isEqualTo(title);
		assertThat(body.get("instance").asString()).isEqualTo(instance);
		return body;
	}

}
