package com.olivamore.api.process;

import com.olivamore.api.TestcontainersConfiguration;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=process-type")
class ProcessTypeApiIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	@Autowired
	RateLimiter rateLimiter;

	@Autowired
	JdbcTemplate jdbc;

	RestClient http;

	String admin;

	@BeforeEach
	void setUp() {
		http = RestClient.create("http://localhost:" + port);
		rateLimiter.clear();
		admin = send(HttpMethod.POST, "/api/auth/login", null,
			"{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}").json().get("token").asString();
	}

	@Test
	void descriptionIsKeptWhenAbsentAndClearedWhenNull() {
		long id = create("Aciklamali", "Ilk aciklama");

		assertThat(patch(id, "{\"name\":\"Aciklamali 2\"}").data().get("description").asString()).isEqualTo("Ilk aciklama");

		JsonNode cleared = patch(id, "{\"description\":null}").data();
		assertThat(cleared.get("description").isNull()).isTrue();
		assertThat(cleared.get("name").asString()).isEqualTo("Aciklamali 2");
	}

	@Test
	void nullMeansNoChangeForOtherFields() {
		long id = create("Degismeyen", null);

		JsonNode data = patch(id, "{\"name\":null,\"category\":null,\"is_active\":null}").data();

		assertThat(data.get("name").asString()).isEqualTo("Degismeyen");
		assertThat(data.get("category").asString()).isEqualTo("quality");
		assertThat(data.get("is_active").asBoolean()).isTrue();
	}

	@Test
	void updatedAtChangesOnlyWithARealChange() {
		long id = create("Damgali", null);
		jdbc.update("update process_types set updated_at = '2020-01-01 00:00:00' where id = ?", id);

		patch(id, "{\"name\":\"Damgali\",\"is_active\":true}");
		assertThat(updatedAt(id)).isEqualTo(LocalDateTime.of(2020, 1, 1, 0, 0));

		patch(id, "{\"is_active\":false}");
		assertThat(updatedAt(id)).isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
	}

	@Test
	void legacyCategoryIsReadableButNotAssignable() {
		assertThat(send(HttpMethod.GET, "/api/process-types/5", admin, null).json().get("data").get("category").asString())
			.isEqualTo("machine_process");

		Response r = send(HttpMethod.POST, "/api/process-types", admin,
			"{\"name\":\"Eski Kategori\",\"category\":\"machine_process\"}");

		assertThat(r.status).as(r.body).isEqualTo(422);
		assertThat(r.body).contains("The selected category is invalid.");
	}

	@Test
	void missingIdIs404AndMissingPermissionIs403() {
		assertThat(send(HttpMethod.GET, "/api/process-types/999999", admin, null).status).isEqualTo(404);

		String noRole = send(HttpMethod.POST, "/api/auth/register", null, """
			{"name":"Rolsuz","email":"rolsuz@olivamore.com","password":"gizli-sifre-123","password_confirmation":"gizli-sifre-123"}
			""").json().get("token").asString();

		assertThat(send(HttpMethod.GET, "/api/process-types/999999", noRole, null).status).isEqualTo(403);
	}

	private long create(String name, String description) {
		String body = "{\"name\":\"" + name + "\",\"category\":\"quality\""
			+ (description == null ? "" : ",\"description\":\"" + description + "\"") + "}";
		Response r = send(HttpMethod.POST, "/api/process-types", admin, body);
		assertThat(r.status).as(r.body).isEqualTo(201);
		return r.data().get("id").asLong();
	}

	private Response patch(long id, String body) {
		Response r = send(HttpMethod.PATCH, "/api/process-types/" + id, admin, body);
		assertThat(r.status).as(r.body).isEqualTo(200);
		return r;
	}

	private LocalDateTime updatedAt(long id) {
		return jdbc.queryForObject("select updated_at from process_types where id = ?", LocalDateTime.class, id);
	}

	private Response send(HttpMethod method, String path, String token, String body) {
		RestClient.RequestBodySpec spec = http.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
		if (token != null) {
			spec.header("Authorization", "Bearer " + token);
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

		JsonNode data() {
			return json().get("data");
		}

	}

}
