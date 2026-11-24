package com.olivamore.api.inventory;

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
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tank seviyesi (docs/GREENOPS_FEATURES.md 4): geometri girilir, cetvel okumasi litre ve kg'ye cevrilir, o anki
 * defter miktariyla fark; okuma stoku degistirmez. Geometri: ic cap 1000 mm, silindir 2000 mm, duz taban
 * (1 m = 785,40 L). Demo veri: tip 3 Gemlik zeytinyagi (LITRE), esleme 4 asitlik.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TankLevelIT {

	private static final int GEMLIK = 3;
	private static final int ACIDITY = 4;

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
	void readingIsConvertedAndComparedWithTheBookWithoutTouchingStock() {
		long tank = tank("LITRE");
		JsonNode geometry = data(api.put("/api/storage-units/" + tank + "/geometry", flat()));
		open(tank, "700");

		Response r = api.post("/api/storage-units/" + tank + "/level-readings",
			Map.of("measured_mm", 1000, "note", "Sabah"));

		assertThat(geometry.get("max_reading_mm").decimalValue()).isEqualByComparingTo("2000");
		assertThat(geometry.get("full_litres").decimalValue()).isEqualByComparingTo("1570.80");
		assertThat(r.status()).as(r.body()).isEqualTo(201);
		JsonNode reading = data(r);
		assertThat(reading.get("calculated_litre").decimalValue()).isEqualByComparingTo("785.40");
		assertThat(reading.get("book_quantity").decimalValue()).isEqualByComparingTo("700");
		assertThat(reading.get("unit").asString()).isEqualTo("LITRE");
		assertThat(reading.get("difference").decimalValue()).isEqualByComparingTo("85.40");
		assertThat(reading.get("difference_percent").decimalValue()).isEqualByComparingTo("12.20");
		BigDecimal density = reading.get("density_kg_per_litre").decimalValue();
		assertThat(reading.get("calculated_kg").decimalValue())
			.isEqualByComparingTo(new BigDecimal("785.40").multiply(density).setScale(2, RoundingMode.HALF_UP));
		assertThat(reading.get("measured_by").get("name").asString()).isNotBlank();
		assertThat(stock(tank)).isEqualByComparingTo("700");
		assertThat(data(api.get("/api/storage-units/" + tank + "/level-readings"))).hasSize(1);
	}

	@Test
	void kilogramTankComparesInKilograms() {
		long tank = tank("KG");
		api.put("/api/storage-units/" + tank + "/geometry", flat());
		open(tank, "500");

		JsonNode reading = data(api.post("/api/storage-units/" + tank + "/level-readings", Map.of("measured_mm", 1000)));

		assertThat(reading.get("unit").asString()).isEqualTo("KG");
		assertThat(reading.get("difference").decimalValue()).isEqualByComparingTo(
			reading.get("calculated_kg").decimalValue().subtract(reading.get("book_quantity").decimalValue()));
	}

	@Test
	void readingNeedsAGeometryAndMustFitTheTank() {
		long tank = tank("LITRE");

		Response noGeometry = api.post("/api/storage-units/" + tank + "/level-readings", Map.of("measured_mm", 100));
		api.put("/api/storage-units/" + tank + "/geometry", flat());
		Response tooHigh = api.post("/api/storage-units/" + tank + "/level-readings", Map.of("measured_mm", 2500));
		Response empty = api.post("/api/storage-units/" + tank + "/level-readings", Map.of("measured_mm", 10));

		assertThat(noGeometry.status()).isEqualTo(422);
		assertThat(tooHigh.status()).isEqualTo(422);
		assertThat(empty.status()).as(empty.body()).isEqualTo(201);
		// bos tank: yogunluk bilinmez, kg yok; litre tankta fark litreyle
		assertThat(data(empty).get("calculated_kg").isNull()).isTrue();
		assertThat(data(empty).get("difference").decimalValue()).isEqualByComparingTo("7.85");
	}

	@Test
	void geometryCanBeClearedAndIsValidated() {
		long tank = tank("LITRE");
		api.put("/api/storage-units/" + tank + "/geometry", flat());

		Response bad = api.put("/api/storage-units/" + tank + "/geometry", Map.of("inner_diameter_mm", 0,
			"cylinder_height_mm", 2000));
		Response cleared = api.delete("/api/storage-units/" + tank + "/geometry");

		assertThat(bad.status()).isEqualTo(422);
		assertThat(bad.json().get("errors").has("inner_diameter_mm")).isTrue();
		assertThat(cleared.status()).isEqualTo(204);
		assertThat(api.get("/api/storage-units/" + tank + "/geometry").json().get("data").isNull()).isTrue();
	}

	@Test
	void demoTanksHaveTheFactoryModelGeometry() {
		long tank = jdbc.queryForObject("select id from storage_units where code = 'TOPLAMA-GEM-01'", Long.class);

		JsonNode geometry = data(api.get("/api/storage-units/" + tank + "/geometry"));
		Response reading = api.post("/api/storage-units/" + tank + "/level-readings", Map.of("measured_mm", 2000));

		// 11000 modeli: ic cap 1909,86 mm, govde 3750 mm, koniler 12 / 11 derece, cetvel ofseti 75 mm
		assertThat(geometry.get("inner_diameter_mm").decimalValue()).isEqualByComparingTo("1909.86");
		assertThat(geometry.get("full_litres").decimalValue()).isBetween(new BigDecimal("11000"),
			new BigDecimal("11200"));
		assertThat(reading.status()).as(reading.body()).isEqualTo(201);
		assertThat(data(reading).get("calculated_litre").decimalValue()).isPositive();
	}

	private static Map<String, Object> flat() {
		return Map.of("inner_diameter_mm", 1000, "cylinder_height_mm", 2000, "bottom_cone_deg", 0, "top_cone_deg", 0);
	}

	private long tank(String unit) {
		return id(api.post("/api/storage-units", Map.of("name", "Seviye", "code", "LEVEL-" + codes.incrementAndGet(),
			"capacity", 5000, "capacity_unit", unit)));
	}

	private void open(long tank, String litres) {
		Response r = api.post("/api/storage-movements/opening", Map.of("storage_unit_id", tank,
			"production_product_type_id", GEMLIK, "quantity", new BigDecimal(litres), "unit", "LITRE",
			"attribute_values", List.of(Map.of("production_product_attribute_definition_id", ACIDITY,
				"value_decimal", new BigDecimal("0.3")))));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
	}

	private BigDecimal stock(long tank) {
		return jdbc.queryForObject("select coalesce(sum(quantity), 0) from materials where current_location_type"
			+ " = 'storage_unit' and current_location_id = ? and status = 'active'", BigDecimal.class, tank);
	}

}
