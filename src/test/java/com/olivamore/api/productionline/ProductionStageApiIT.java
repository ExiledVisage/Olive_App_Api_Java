package com.olivamore.api.productionline;

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
	properties = "olive.test.context=production-stage")
class ProductionStageApiIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	@Autowired
	RateLimitBuckets rateLimiter;

	RestClient http;

	String admin;

	long lineId;

	@BeforeEach
	void setUp() {
		http = RestClient.create("http://localhost:" + port);
		rateLimiter.clear();
		admin = send(HttpMethod.POST, "/api/auth/login",
			"{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}").json().get(TestAuth.ACCESS_TOKEN).asString();
		Response line = send(HttpMethod.POST, "/api/production-lines", """
			{"name":"Test Hatti %d",
			 "input_production_products":[{"production_product_id":1}],
			 "output_production_products":[{"production_product_id":2}]}
			""".formatted(System.nanoTime()));
		assertThat(line.status).as(line.body).isEqualTo(200);
		lineId = line.data().get("id").asLong();
	}

	@Test
	void nullFlagsUseDefaults() {
		JsonNode stage = create("Varsayilan", 1, ",\"is_required\":null,\"requires_approval\":null");

		assertThat(stage.get("is_required").asBoolean()).isTrue();
		assertThat(stage.get("requires_approval").asBoolean()).isFalse();
	}

	@Test
	void processTypeIsClearedOnlyByExplicitNull() {
		long id = create("Prosesli", 1, ",\"process_type_id\":1").get("id").asLong();

		assertThat(patch(id, "{\"name\":\"Yeni\"}").get("process_type_id").asLong()).isEqualTo(1);
		assertThat(patch(id, "{\"process_type_id\":null}").get("process_type_id").isNull()).isTrue();
	}

	@Test
	void unknownProcessTypeIsRejected() {
		Response r = send(HttpMethod.POST, stages(),
			"{\"name\":\"X\",\"stage_order\":1,\"process_type_id\":999999}");

		assertThat(r.status).as(r.body).isEqualTo(422);
		assertThat(r.body).contains("The selected process type id is invalid.");
	}

	@Test
	void ordersStayContiguousAfterMovesAndDeletes() {
		long a = create("A", 1, "").get("id").asLong();
		create("B", 2, "");
		long c = create("C", 3, "").get("id").asLong();

		patch(c, "{\"stage_order\":1}");
		assertThat(names()).isEqualTo("[C, A, B]");

		patch(c, "{\"stage_order\":99}");
		assertThat(names()).isEqualTo("[A, B, C]");

		assertThat(send(HttpMethod.DELETE, stages() + "/" + a, null).status).isEqualTo(200);
		assertThat(names()).isEqualTo("[B, C]");
		assertThat(send(HttpMethod.GET, stages(), null).json().get("data").get(1).get("stage_order").asInt())
			.isEqualTo(2);
	}

	@Test
	void unknownLineProductIsRejectedWithItsPath() {
		Response r = send(HttpMethod.PATCH, "/api/production-lines/" + lineId,
			"{\"input_production_products\":[{\"production_product_id\":999999}]}");

		assertThat(r.status).as(r.body).isEqualTo(422);
		assertThat(r.body).contains("The selected input_production_products.0.production_product_id is invalid.");
	}

	private String names() {
		java.util.List<String> names = new java.util.ArrayList<>();
		send(HttpMethod.GET, stages(), null).json().get("data").forEach(s -> names.add(s.get("name").asString()));
		return names.toString();
	}

	private JsonNode create(String name, int order, String extra) {
		Response r = send(HttpMethod.POST, stages(),
			"{\"name\":\"" + name + "\",\"stage_order\":" + order + extra + "}");
		assertThat(r.status).as(r.body).isEqualTo(201);
		return r.data();
	}

	private JsonNode patch(long id, String body) {
		Response r = send(HttpMethod.PATCH, stages() + "/" + id, body);
		assertThat(r.status).as(r.body).isEqualTo(200);
		return r.data();
	}

	private String stages() {
		return "/api/production-lines/" + lineId + "/stages";
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

		JsonNode data() {
			return json().get("data");
		}

	}

}
