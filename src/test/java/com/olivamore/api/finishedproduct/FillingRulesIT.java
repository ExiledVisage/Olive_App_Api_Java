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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Dolum kurallari, her test kendi tankiyla: tarif orani toleransi (%2), tarifteki asitlik araligi, tanktan cekilen
 * yagin fire toleransi (%2), materyallerden oransal tuketim, tamamlanmada yag, ambalaj ve son urun stogu, durum
 * gecisleri ve yetki.
 * Demo veri: urun 1 (tarif 1: %100 Gemlik, asitlik 0.3 +-0.05, 0.25 L, ambalaj kalemleri 1, 8, 12, 17),
 * urun 15 (tarif 5: %70 Gemlik + %30 Ayvalik, asitlik 0.5 +-0.05).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FillingRulesIT {

	private static final int GEMLIK = 3;
	private static final int AYVALIK = 4;
	private static final int ACIDITY = 4;
	private static final int GEMLIK_03 = 1;
	private static final int BLEND = 15;
	private static final List<Integer> GEMLIK_03_PACKAGING = List.of(1, 8, 12, 17);

	private final AtomicInteger codes = new AtomicInteger();

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
	void blendRatioMayDifferFromTheRecipeByAtMostTwoPercent() {
		long withinTolerance = tank(Map.of(GEMLIK, "710", AYVALIK, "290"), "0.5");
		long outside = tank(Map.of(GEMLIK, "730", AYVALIK, "270"), "0.5");

		assertThat(plan(BLEND, 10, withinTolerance, null).status()).isEqualTo(201);
		Response rejected = plan(BLEND, 10, outside, null);
		assertThat(rejected.status()).isEqualTo(422);
		assertThat(detail(rejected)).isEqualTo("Tank blend oranı tarife uymuyor. 'Gemlik Zeytinyağı' için beklenen oran: "
			+ "70%, tankta bulunan: 73%.");
	}

	@Test
	void estimatedAttributeMustBeWithinTheRecipeRange() {
		long tooAcidic = tank(Map.of(GEMLIK, "100"), "0.4");

		Response r = plan(GEMLIK_03, 10, tooAcidic, null);

		assertThat(r.status()).isEqualTo(422);
		assertThat(detail(r)).startsWith("Tahmini Asitlik değeri (0.4)");
	}

	@Test
	void drawnOilMustCoverTheBottlesAndMayExceedThemByTwoPercent() {
		long tank = tank(Map.of(GEMLIK, "100"), "0.3");

		Response tooLittle = plan(GEMLIK_03, 10, tank, "2.45");
		Response tooMuch = plan(GEMLIK_03, 10, tank, "2.56");
		Response withinLoss = plan(GEMLIK_03, 10, tank, "2.55");

		assertThat(detail(tooLittle)).startsWith("10 adet × 0.25 L = 2.5 L yağ gerekiyor");
		assertThat(detail(tooMuch)).contains("%2 fire toleransını aşıyor");
		assertThat(withinLoss.status()).as(withinLoss.body()).isEqualTo(201);
	}

	@Test
	void oilIsDrawnFromEachMaterialInProportionToItsShare() {
		long tank = tank(Map.of(), null);
		long large = open(tank, GEMLIK, "60", "0.3");
		long small = open(tank, GEMLIK, "40", "0.3");

		long run = id(plan(GEMLIK_03, 20, tank, null));

		assertThat(used(run, large)).isEqualByComparingTo("3");
		assertThat(used(run, small)).isEqualByComparingTo("2");
	}

	@Test
	void completingARunConsumesOilAndPackagingAndAddsBottles() {
		long tank = tank(Map.of(GEMLIK, "100"), "0.3");
		Map<Integer, Long> packagingBefore = packagingStock();
		long bottlesBefore = bottles(GEMLIK_03);

		long run = id(plan(GEMLIK_03, 20, tank, null));
		assertThat(status(run, "in_progress").status()).isEqualTo(200);
		Response completed = status(run, "completed");

		assertThat(completed.status()).as(completed.body()).isEqualTo(200);
		assertThat(oilIn(tank)).isCloseTo(new BigDecimal("95"), within(new BigDecimal("0.0001")));
		packagingStock().forEach((item, stock) -> assertThat(stock).as("ambalaj kalemi " + item)
			.isEqualTo(packagingBefore.get(item) - 20));
		assertThat(bottles(GEMLIK_03)).isEqualTo(bottlesBefore + 20);
	}

	@Test
	void runStatusMovesPlannedThenInProgressThenCompleted() {
		long tank = tank(Map.of(GEMLIK, "100"), "0.3");
		long run = id(plan(GEMLIK_03, 4, tank, null));

		Response skip = status(run, "completed");

		assertThat(skip.status()).isEqualTo(422);
		assertThat(detail(skip)).isEqualTo("'planned' durumundan 'completed' durumuna geçiş yapılamaz.");
		assertThat(status(run, "in_progress").status()).isEqualTo(200);
		assertThat(status(run, "in_progress").status()).isEqualTo(422);
	}

	@Test
	void onlyAPlannedRunCanBeDeleted() {
		long tank = tank(Map.of(GEMLIK, "100"), "0.3");
		long planned = id(plan(GEMLIK_03, 4, tank, null));
		long started = id(plan(GEMLIK_03, 4, tank, null));
		status(started, "in_progress");

		Response rejected = api.delete("/api/finished-product-runs/" + started);

		assertThat(detail(rejected)).isEqualTo("Sadece planlanmış (planned) durumdaki run silinebilir.");
		assertThat(api.delete("/api/finished-product-runs/" + planned).status()).isEqualTo(200);
	}

	@Test
	void plannerCannotPlanARun() {
		long tank = tank(Map.of(GEMLIK, "100"), "0.3");

		Response r = api.as("planner@olivamore.com").post("/api/finished-product-runs",
			Map.of("finished_product_id", GEMLIK_03, "quantity", 1, "source", Map.of("storage_unit_id", tank)));

		assertThat(r.status()).isEqualTo(403);
	}

	// ---- yardimcilar -------------------------------------------------------

	/** Birden cok tipe izin veren tank; her tip icin bir acilis (asitlik ayni). */
	private long tank(Map<Integer, String> contents, String acidity) {
		long tank = id(api.post("/api/storage-units", Map.of("name", "Dolum kurali", "code", "FILL-RULE-"
			+ codes.incrementAndGet(), "capacity", 5000, "capacity_unit", "LITRE",
			"allow_multiple_production_product_types", true)));
		contents.forEach((type, quantity) -> open(tank, type, quantity, acidity));
		return tank;
	}

	/** @return materyal id */
	private long open(long tank, int type, String quantity, String acidity) {
		Response r = api.post("/api/storage-movements/opening", Map.of("storage_unit_id", tank,
			"production_product_type_id", type, "quantity", new BigDecimal(quantity), "unit", "LITRE",
			"attribute_values", List.of(Map.of("production_product_attribute_definition_id", ACIDITY,
				"value_decimal", new BigDecimal(acidity)))));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
		return r.json().get("data").get("material_id").asLong();
	}

	private Response plan(int product, int bottles, long tank, String sourceQuantity) {
		Map<String, Object> source = new HashMap<>();
		source.put("storage_unit_id", tank);
		if (sourceQuantity != null) {
			source.put("quantity", new BigDecimal(sourceQuantity));
		}
		return api.post("/api/finished-product-runs", Map.of("finished_product_id", product, "quantity", bottles,
			"source", source));
	}

	private Response status(long run, String status) {
		return api.patch("/api/finished-product-runs/" + run + "/status", Map.of("status", status));
	}

	private static String detail(Response r) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		return r.json().get("detail").asString();
	}

	private BigDecimal used(long run, long material) {
		return jdbc.queryForObject("select quantity_used from finished_product_run_materials"
			+ " where finished_product_run_id = ? and source_material_id = ?", BigDecimal.class, run, material);
	}

	private BigDecimal oilIn(long tank) {
		return jdbc.queryForObject("select coalesce(sum(quantity), 0) from materials where current_location_type"
			+ " = 'storage_unit' and current_location_id = ? and status = 'active'", BigDecimal.class, tank);
	}

	private Map<Integer, Long> packagingStock() {
		Map<Integer, Long> stock = new HashMap<>();
		for (int item : GEMLIK_03_PACKAGING) {
			stock.put(item, jdbc.queryForObject("select coalesce(sum(quantity_remaining), 0) from packaging_lots"
				+ " where packaging_item_id = ?", Long.class, item));
		}
		return stock;
	}

	private long bottles(int product) {
		return jdbc.queryForObject("select total_stock from finished_products where id = ?", Long.class, product);
	}

}
