package com.olivamore.api.finishedproduct;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.OilInLitres;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dolumda kirik / hatali ambalaj (docs/GREENOPS_FEATURES.md 2): baslamis ya da bitmis kosuda, urunun ambalajindan;
 * kosunun tahsis ettigi lottan duser, uretim adedi degismez; kalem basina fire orani; geri alininca lota doner.
 * Demo veri: urun 1 (Gemlik 0.3), ambalaj kalemleri 1, 8, 12, 17; tip 3 Gemlik, esleme 4 asitlik.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackagingScrapIT {

	private static final int GEMLIK = 3;
	private static final int ACIDITY = 4;
	private static final int GEMLIK_03 = 1;
	private static final int BOTTLE = 1;

	private final AtomicInteger codes = new AtomicInteger();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		OilInLitres.apply(jdbc);
	}

	@Test
	void scrapComesFromTheRunsLotAndShowsTheRate() {
		long run = startedRun(20);
		long lot = allocatedLot(run, BOTTLE);
		int before = remaining(lot);

		JsonNode detail = data(scrap(run, Map.of("packaging_item_id", BOTTLE, "quantity", 5, "reason", "broken",
			"note", "Hatta kirildi")));

		assertThat(remaining(lot)).isEqualTo(before - 5);
		JsonNode entry = detail.get("packaging_scrap").get("entries").get(0);
		assertThat(entry.get("packaging_lot_id").asLong()).isEqualTo(lot);
		assertThat(entry.get("reason").asString()).isEqualTo("broken");
		assertThat(entry.get("note").asString()).isEqualTo("Hatta kirildi");
		JsonNode item = detail.get("packaging_scrap").get("items").valueStream()
			.filter(i -> i.get("packaging_item_id").asInt() == BOTTLE).findFirst().orElseThrow();
		// 5 / (20 + 5) x 100
		assertThat(item.get("used").asLong()).isEqualTo(20);
		assertThat(item.get("scrapped").asLong()).isEqualTo(5);
		assertThat(item.get("scrap_rate_percent").decimalValue()).isEqualByComparingTo("20");
		assertThat(detail.get("quantity").asInt()).isEqualTo(20);
	}

	@Test
	void undoGivesTheQuantityBackToTheLot() {
		long run = startedRun(10);
		long lot = allocatedLot(run, BOTTLE);
		int before = remaining(lot);
		long entry = data(scrap(run, Map.of("packaging_item_id", BOTTLE, "quantity", 3, "reason", "defective")))
			.get("packaging_scrap").get("entries").get(0).get("id").asLong();

		Response undone = api.delete("/api/finished-product-runs/" + run + "/packaging-scrap/" + entry);

		assertThat(undone.status()).as(undone.body()).isEqualTo(200);
		assertThat(remaining(lot)).isEqualTo(before);
		assertThat(data(undone).get("packaging_scrap").get("entries")).isEmpty();
		assertThat(api.delete("/api/finished-product-runs/" + run + "/packaging-scrap/" + entry).status())
			.isEqualTo(404);
	}

	@Test
	void scrapNeedsAStartedRunAProductPackagingAndAKnownReason() {
		long tank = tank();
		long planned = id(plan(4, tank));
		long started = startedRun(4);
		long foreignItem = jdbc.queryForObject("select id from packaging_items where id not in (select"
			+ " packaging_item_id from finished_product_components where finished_product_id = ?) order by id limit 1",
			Long.class, GEMLIK_03);

		Response onPlanned = api.post("/api/finished-product-runs/" + planned + "/packaging-scrap",
			Map.of("packaging_item_id", BOTTLE, "quantity", 1, "reason", "broken"));
		Response foreign = api.post("/api/finished-product-runs/" + started + "/packaging-scrap",
			Map.of("packaging_item_id", foreignItem, "quantity", 1, "reason", "broken"));
		Response badReason = api.post("/api/finished-product-runs/" + started + "/packaging-scrap",
			Map.of("packaging_item_id", BOTTLE, "quantity", 1, "reason", "kayboldu"));

		assertThat(onPlanned.status()).as(onPlanned.body()).isEqualTo(422);
		assertThat(foreign.status()).as(foreign.body()).isEqualTo(422);
		assertThat(foreign.json().get("errors").has("packaging_item_id")).isTrue();
		assertThat(badReason.status()).as(badReason.body()).isEqualTo(422);
		assertThat(badReason.json().get("errors").has("reason")).isTrue();
	}

	@Test
	void completedRunStillTakesScrapWithoutChangingItsBottles() {
		long run = startedRun(8);
		assertThat(status(run, "completed").status()).isEqualTo(200);

		JsonNode detail = data(scrap(run, Map.of("packaging_item_id", BOTTLE, "quantity", 2, "reason", "other")));

		assertThat(detail.get("status").asString()).isEqualTo("completed");
		assertThat(detail.get("quantity").asInt()).isEqualTo(8);
		assertThat(detail.get("packaging_scrap").get("entries")).hasSize(1);
	}

	// ---- yardimcilar -------------------------------------------------------

	private Response scrap(long run, Map<String, Object> body) {
		Response r = api.post("/api/finished-product-runs/" + run + "/packaging-scrap", body);
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		return r;
	}

	private long startedRun(int bottles) {
		long run = id(plan(bottles, tank()));
		Response started = status(run, "in_progress");
		assertThat(started.status()).as(started.body()).isEqualTo(200);
		return run;
	}

	private long allocatedLot(long run, int item) {
		return jdbc.queryForObject("select packaging_lot_id from finished_product_run_packagings"
			+ " where finished_product_run_id = ? and packaging_item_id = ? order by id limit 1", Long.class, run, item);
	}

	private int remaining(long lot) {
		return jdbc.queryForObject("select quantity_remaining from packaging_lots where id = ?", Integer.class, lot);
	}

	private long tank() {
		long tank = id(api.post("/api/storage-units", Map.of("name", "Fire", "code", "SCRAP-" + codes.incrementAndGet(),
			"capacity", 5000, "capacity_unit", "LITRE")));
		Response r = api.post("/api/storage-movements/opening", Map.of("storage_unit_id", tank,
			"production_product_type_id", GEMLIK, "quantity", new BigDecimal("100"), "unit", "LITRE",
			"attribute_values", List.of(Map.of("production_product_attribute_definition_id", ACIDITY,
				"value_decimal", new BigDecimal("0.3")))));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
		return tank;
	}

	private Response plan(int bottles, long tank) {
		Map<String, Object> source = new HashMap<>();
		source.put("storage_unit_id", tank);
		return api.post("/api/finished-product-runs", Map.of("finished_product_id", GEMLIK_03, "quantity", bottles,
			"source", source));
	}

	private Response status(long run, String status) {
		return api.patch("/api/finished-product-runs/" + run + "/status", Map.of("status", status));
	}

}
