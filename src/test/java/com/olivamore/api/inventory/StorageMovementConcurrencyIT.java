package com.olivamore.api.inventory;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ApiClient;
import com.olivamore.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ayni tanka eszamanli girisler tank satiri kilitlenerek sirayla islenir: ikisi birden kapasiteyi bos
 * gorup tanki tasiramaz.
 */
@ApiIntegrationTest
class StorageMovementConcurrencyIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void concurrentInboundsCannotOverfillTank() throws Exception {
		Long unit = jdbc.queryForObject("insert into storage_units (name, code, capacity, capacity_unit, created_at, updated_at)"
			+ " values ('Kilit', 'TEST-LOCK', 100, 'LITRE', now(), now()) returning id", Long.class);
		RestClient http = ApiClient.http(port);
		String token = JSON.readTree(ApiClient.call(http, "POST", "/api/auth/login", null,
			JSON.readTree("{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}")).body()).get(TestAuth.ACCESS_TOKEN).asString();
		String body = "{\"storage_unit_id\":" + unit + ",\"production_product_type_id\":3,\"quantity\":60,\"unit\":\"LITRE\"}";

		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Future<ApiClient.Response>> calls = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Callable<ApiClient.Response> call = () -> ApiClient.call(http, "POST", "/api/storage-movements/opening", token,
				JSON.readTree(body));
			calls.add(pool.submit(call));
		}
		List<Integer> statuses = new ArrayList<>();
		for (Future<ApiClient.Response> f : calls) {
			statuses.add(f.get().status());
		}
		pool.shutdown();

		assertThat(statuses).containsOnlyOnce(201).contains(422);
		assertThat(jdbc.queryForObject("select sum(current_quantity) from storage_unit_balances where storage_unit_id = ?",
			java.math.BigDecimal.class, unit)).isEqualByComparingTo("60");
	}

}
