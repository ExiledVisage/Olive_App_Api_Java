package com.olivamore.api.process;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import com.olivamore.api.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = "olive.test.context=process-type-field")
class ProcessTypeFieldApiIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final int TYPE = 1;

	@LocalServerPort
	int port;

	@Autowired
	RateLimitBuckets rateLimiter;

	RestClient http;

	String admin;

	@BeforeEach
	void setUp() {
		http = RestClient.create("http://localhost:" + port);
		rateLimiter.clear();
		admin = send(HttpMethod.POST, "/api/auth/login",
			"{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}").json().get(TestAuth.ACCESS_TOKEN).asString();
	}

	@Test
	void optionsAreStoredAndClearedAsJson() {
		long id = create("secenekli", "\"options\":[\"a\",{\"b\":1}]");
		JsonNode data = send(HttpMethod.GET, path(id), null).json().get("data");
		assertThat(data.get("options").toString()).isEqualTo("[\"a\",{\"b\":1}]");

		Response cleared = send(HttpMethod.PATCH, path(id), "{\"options\":null}");
		assertThat(cleared.status).as(cleared.body).isEqualTo(200);
		assertThat(cleared.json().get("data").get("options").isNull()).isTrue();
	}

	@Test
	void scalarOptionsAreRejected() {
		Response r = send(HttpMethod.POST, base(),
			"{\"field_key\":\"skaler\",\"label\":\"X\",\"data_type\":\"text\",\"options\":5}");

		assertThat(r.status).as(r.body).isEqualTo(422);
		assertThat(r.body).contains("The options field must be an array.");
	}

	@Test
	void patchLimitsApplyToTheWrappedValue() {
		long id = create("birimli", "\"unit\":\"kg\"");

		Response tooLong = send(HttpMethod.PATCH, path(id), "{\"unit\":\"" + "x".repeat(51) + "\"}");
		assertThat(tooLong.status).as(tooLong.body).isEqualTo(422);
		assertThat(tooLong.json().get("errors").get("unit").get(0).asString())
			.isEqualTo("The unit field must not be greater than 50 characters.");

		JsonNode kept = send(HttpMethod.PATCH, path(id), "{\"label\":\"Yeni\"}").json().get("data");
		assertThat(kept.get("unit").asString()).isEqualTo("kg");
		assertThat(send(HttpMethod.PATCH, path(id), "{\"unit\":null}").json().get("data").get("unit").isNull())
			.isTrue();
	}

	@Test
	void missingProcessTypeIs404() {
		Response r = send(HttpMethod.GET, "/api/process-types/999999/fields", null);

		assertThat(r.status).isEqualTo(404);
		assertThat(r.body).contains("\"detail\":\"ProcessType 999999 not found.\"");
	}

	@Test
	void stageExtensionTypesListKeepsItsFieldsAndOptions() {
		JsonNode types = send(HttpMethod.GET, "/api/stage-extension-types", null).json().get("data");

		assertThat(types.size()).isPositive();
		assertThat(types.toString()).contains("\"options_json\":[\"inbound\",\"outbound\",\"transfer\"]");
	}

	private long create(String key, String extra) {
		Response r = send(HttpMethod.POST, base(), "{\"field_key\":\"" + key
			+ "\",\"label\":\"Alan\",\"data_type\":\"text\"," + extra + "}");
		assertThat(r.status).as(r.body).isEqualTo(201);
		return r.json().get("data").get("id").asLong();
	}

	private static String base() {
		return "/api/process-types/" + TYPE + "/fields";
	}

	private static String path(long id) {
		return base() + "/" + id;
	}

	private Response send(HttpMethod method, String path, String body) {
		RestClient.RequestBodySpec spec = http.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
		if (admin != null) {
			spec.header("Authorization", "Bearer " + admin);
		}
		if (body != null) {
			spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.exchange((request, response) -> new Response(response.getStatusCode().value(),
			new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private record Response(int status, String body) {

		JsonNode json() {
			return JSON.readTree(body);
		}

	}

}
