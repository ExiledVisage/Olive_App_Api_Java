package com.olivamore.api.finishedproduct;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Raf adresi deposuyla birlikte: baska deponun rafi guncellenmez, silinmez (404). Demo veri: raf 1 Ana Depo'da. */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageSlotRulesIT {

	private static final String FOREIGN = "/api/finished-product-storages/2/slots/1";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void slotOfAnotherStorageIsNotFound() {
		for (Response r : List.of(api.patch(FOREIGN, Map.of("name", "X1")), api.delete(FOREIGN))) {
			assertThat(r.status()).as(r.body()).isEqualTo(404);
			assertThat(r.json().get("detail").asString()).isEqualTo("Bu slot bu depoya ait değil.");
		}
		assertThat(jdbc.queryForObject("select name from finished_product_storage_slots where id = 1", String.class))
			.isEqualTo("A1");
	}

	@Test
	void slotOfItsOwnStorageIsUpdated() {
		Response r = api.patch("/api/finished-product-storages/1/slots/1", Map.of("name", "A1"));

		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

}
