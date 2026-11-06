package com.olivamore.api.inventory;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
 * Laravel'den fark: ayni tanka eszamanli girisler tank satiri kilitlenerek sirayla islenir. Laravel'de
 * ikisi de kapasiteyi bos gorup tanki tasirabiliyordu (DEVIATIONS.md).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=storage-movement-concurrency")
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
		RestClient http = LaravelReplay.client(port);
		String token = JSON.readTree(LaravelReplay.call(http, "POST", "/api/auth/login", null,
			JSON.readTree("{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}")).body()).get("token").asString();
		String body = "{\"storage_unit_id\":" + unit + ",\"production_product_type_id\":3,\"quantity\":60,\"unit\":\"LITRE\"}";

		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Future<LaravelReplay.Response>> calls = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Callable<LaravelReplay.Response> call = () -> LaravelReplay.call(http, "POST", "/api/storage-movements/opening", token,
				JSON.readTree(body));
			calls.add(pool.submit(call));
		}
		List<Integer> statuses = new ArrayList<>();
		for (Future<LaravelReplay.Response> f : calls) {
			statuses.add(f.get().status());
		}
		pool.shutdown();

		assertThat(statuses).containsOnlyOnce(201).contains(422);
		assertThat(jdbc.queryForObject("select sum(current_quantity) from storage_unit_balances where storage_unit_id = ?",
			java.math.BigDecimal.class, unit)).isEqualByComparingTo("60");
	}

}
