package com.olivamore.api.production;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/** Teslimatin sabit bilgileri: kamyon plakasi (zorunlu) ve kantar fisi partide, kantar asamasinda degil. */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchDeliveryInfoIT {

	@LocalServerPort
	int port;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void truckPlateIsRequired() {
		Response r = api.post("/api/production-batches", body("T-PLAKA-YOK", "  "));

		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.body()).contains("truck_plate");
	}

	@Test
	void plateAndSlipAreStoredAndChangedOnTheBatch() {
		Map<String, Object> body = body("T-PLAKA-1", " 35 ABC 123 ");
		body.put("weighing_slip_no", "TF-1");
		long batch = id(api.post("/api/production-batches", body));
		String path = "/api/production-batches/" + batch;

		JsonNode created = data(api.get(path));
		assertThat(created.get("truck_plate").asString()).isEqualTo("35 ABC 123");
		assertThat(created.get("weighing_slip_no").asString()).isEqualTo("TF-1");

		assertThat(data(api.patch(path, Map.of("truck_plate", " "))).get("truck_plate").asString())
			.as("bos plaka gonderilirse degismez").isEqualTo("35 ABC 123");
		Map<String, Object> clear = new HashMap<>();
		clear.put("weighing_slip_no", null);
		clear.put("truck_plate", "16 XYZ 9");
		JsonNode changed = data(api.patch(path, clear));
		assertThat(changed.get("truck_plate").asString()).isEqualTo("16 XYZ 9");
		assertThat(changed.get("weighing_slip_no").isNull()).isTrue();
	}

	@Test
	void supplierDeliveriesShowThePlateOfTheBatch() {
		Map<String, Object> body = body("T-PLAKA-2", "06 DEF 45");
		body.put("weighing_slip_no", "TF-2");
		long batch = id(api.post("/api/production-batches", body));

		JsonNode delivery = null;
		for (JsonNode row : api.get("/api/suppliers/1/deliveries").json().get("data")) {
			if (row.get("production_batch_id").asLong() == batch) {
				delivery = row;
			}
		}

		assertThat(delivery).isNotNull();
		assertThat(delivery.get("truck_plate").asString()).isEqualTo("06 DEF 45");
		assertThat(delivery.get("weighing_slip_no").asString()).isEqualTo("TF-2");
	}

	private static Map<String, Object> body(String code, String plate) {
		Map<String, Object> body = new HashMap<>();
		body.put("batch_code", code);
		body.put("input_production_product_type_ids", List.of(1));
		body.put("production_line_id", 1);
		body.put("supplier_id", 1);
		body.put("truck_plate", plate);
		return body;
	}

}
