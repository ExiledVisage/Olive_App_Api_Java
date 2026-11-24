package com.olivamore.api.inventory;

import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.OilInLitres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tank defteri (islem gecmisi): yon, sayfa sinirinda kopmayan bakiye, materyal suzgeci, giristen sonraki karisim
 * degeri (tank analizi dahil), transferde karsi materyal, gelen partinin bilgisi ve ozet. Her islem ayri saniyede
 * (saat elle ilerletilir). Demo veri: tip 3 Gemlik ve 4 Ayvalik zeytinyagi (LITRE), esleme 4 asitlik.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TankLedgerIT {

	private static final int GEMLIK = 3;
	private static final int AYVALIK = 4;
	private static final int ACIDITY = 4;

	private final AtomicInteger codes = new AtomicInteger();

	private Instant clock;

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

	@BeforeEach
	void startClock() {
		clock = Instant.now();
	}

	@AfterEach
	void resetClock() {
		Timestamps.useClock(Clock.systemUTC());
	}

	@Test
	void historyCarriesDirectionBalanceAndMixAfterEachEntry() {
		long tank = tank(Map.of());
		long mix = materialOf(open(tank, GEMLIK, "100", "0.30"));
		open(tank, GEMLIK, "50", "0.60");
		tick();
		Response out = api.post("/api/storage-movements/outbound", Map.of("storage_unit_id", tank, "quantity", 30));
		assertThat(out.status()).as(out.body()).isEqualTo(201);
		open(tank, GEMLIK, "30", "0.70");

		JsonNode rows = content(api.get("/api/storage-units/" + tank + "/movement-history"));

		assertThat(rows).hasSize(4);
		assertThat(rows.get(0).get("direction").asString()).isEqualTo("in");
		assertThat(rows.get(0).get("balance_after").decimalValue()).isEqualByComparingTo("150");
		// (120 x 0.40 + 30 x 0.70) / 150 = 0.46: cikis agirligi azaltir
		assertThat(acidityAfter(rows.get(0))).isEqualByComparingTo("0.46");
		assertThat(acidityAfter(rows.get(0))).isEqualByComparingTo(acidity(mix));
		assertThat(rows.get(1).get("direction").asString()).isEqualTo("out");
		assertThat(rows.get(1).get("balance_after").decimalValue()).isEqualByComparingTo("120");
		assertThat(rows.get(1).get("mix_after").isNull()).isTrue();
		assertThat(acidityAfter(rows.get(2))).isEqualByComparingTo("0.40");
		assertThat(rows.get(3).get("balance_after").decimalValue()).isEqualByComparingTo("100");
		assertThat(acidityAfter(rows.get(3))).isEqualByComparingTo("0.30");
		// eski alanlar aynen
		assertThat(rows.get(0).get("material_id").asLong()).isEqualTo(mix);
		assertThat(rows.get(0).get("movement_type").asString()).isEqualTo("OPENING");
		assertThat(rows.get(0).get("source").get("material_id").asLong())
			.isEqualTo(rows.get(0).get("source_material_id").asLong());

		JsonNode second = content(api.get("/api/storage-units/" + tank + "/movement-history?page=1&size=3"));
		assertThat(second).hasSize(1);
		assertThat(second.get(0).get("balance_after").decimalValue()).isEqualByComparingTo("100");

		JsonNode recent = content(api.get("/api/storage-units/" + tank + "/movement-history?date_from="
			+ java.time.LocalDate.ofInstant(clock, ZoneOffset.UTC)));
		assertThat(recent.get(0).get("balance_after").decimalValue()).isEqualByComparingTo("150");
	}

	@Test
	void tankAnalysisReplacesTheMixValueForLaterEntries() {
		long tank = tank(Map.of());
		long mix = materialOf(open(tank, GEMLIK, "1000", "0.30"));
		open(tank, GEMLIK, "500", "0.50");
		tick();
		Response measured = api.post("/api/storage-units/" + tank + "/measurements", Map.of("material_id", mix,
			"attributes", List.of(Map.of("production_product_attribute_definition_id", ACIDITY,
				"value", new BigDecimal("0.41")))));
		assertThat(measured.status()).as(measured.body()).isEqualTo(201);
		open(tank, GEMLIK, "500", "0.30");

		JsonNode rows = content(api.get("/api/storage-units/" + tank + "/movement-history"));

		assertThat(acidityAfter(rows.get(1))).isEqualByComparingTo("0.3667");
		// (1500 x 0.41 + 500 x 0.30) / 2000
		assertThat(acidityAfter(rows.get(0))).isEqualByComparingTo("0.3825");
		assertThat(acidityAfter(rows.get(0))).isEqualByComparingTo(acidity(mix));
		assertThat(rows.get(0).get("mix_after").get(0).get("attribute_key").asString()).isEqualTo("acidity");
	}

	@Test
	void materialFilterAndTransferCounterpartyLeadToTheOtherTanksLedger() {
		long from = tank(Map.of("allow_multiple_production_product_types", true));
		long to = tank(Map.of("allow_multiple_production_product_types", true));
		long gemlik = materialOf(open(from, GEMLIK, "600", "0.30"));
		long ayvalik = materialOf(open(from, AYVALIK, "400", "0.50"));
		tick();
		Response moved = api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", from,
			"to_storage_unit_id", to, "quantity", 100));
		assertThat(moved.status()).as(moved.body()).isEqualTo(200);

		JsonNode rows = content(api.get("/api/storage-units/" + from + "/movement-history?material_id=" + gemlik));

		assertThat(rows).hasSize(2);
		assertThat(rows).allSatisfy(row -> assertThat(row.get("material_id").asLong()).isEqualTo(gemlik));
		JsonNode transferOut = rows.get(0);
		assertThat(transferOut.get("movement_type").asString()).isEqualTo("TRANSFER_OUT");
		assertThat(transferOut.get("balance_after").decimalValue()).isEqualByComparingTo("540");
		long target = transferOut.get("counterparty_material_id").asLong();

		JsonNode other = content(api.get("/api/storage-units/" + to + "/movement-history?material_id=" + target));
		assertThat(other).hasSize(1);
		assertThat(other.get(0).get("counterparty_material_id").asLong()).isEqualTo(gemlik);
		assertThat(other.get(0).get("balance_after").decimalValue()).isEqualByComparingTo("60");
		assertThat(acidityAfter(other.get(0))).isEqualByComparingTo("0.30");

		JsonNode summaries = data(api.get("/api/storage-units/" + from + "/movement-history/summary"));
		assertThat(summaries).hasSize(2);
		JsonNode summary = data(api.get("/api/storage-units/" + from + "/movement-history/summary?material_id="
			+ ayvalik)).get(0);
		assertThat(summary.get("material").get("id").asLong()).isEqualTo(ayvalik);
		assertThat(summary.get("total_in").decimalValue()).isEqualByComparingTo("400");
		assertThat(summary.get("total_out").decimalValue()).isEqualByComparingTo("40");
		assertThat(summary.get("balance").decimalValue()).isEqualByComparingTo("360");
		assertThat(summary.get("in_count").asInt()).isEqualTo(1);
		assertThat(summary.get("out_count").asInt()).isEqualTo(1);
	}

	@Test
	void batchEntryShowsTheBatchItCameFrom() {
		Map<String, Object> entry = jdbc.queryForMap("""
			select m.storage_unit_id, m.material_id, m.id, b.batch_code from storage_movements m
			join materials s on s.id = m.source_material_id
			join production_batches b on b.id = s.production_batch_id
			where s.source_production_stage_id is not null
			order by m.id limit 1
			""");

		JsonNode rows = content(api.get("/api/storage-units/" + entry.get("storage_unit_id")
			+ "/movement-history?material_id=" + entry.get("material_id")));

		JsonNode row = rows.valueStream().filter(r -> r.get("id").asLong() == ((Number) entry.get("id")).longValue())
			.findFirst().orElseThrow();
		assertThat(row.get("source").get("batch_code").asString()).isEqualTo(entry.get("batch_code"));
		assertThat(row.get("source").get("stage_name").asString()).isNotBlank();
		assertThat(row.get("balance_after").decimalValue().signum()).isPositive();
	}

	@Test
	void preSettlingEntryShowsTheEnteredBatchMaterialItself() {
		Map<String, Object> entry = jdbc.queryForMap("""
			select m.storage_unit_id, m.material_id, m.id, b.batch_code from storage_movements m
			join materials s on s.id = m.material_id
			join production_batches b on b.id = s.production_batch_id
			where m.movement_type = 'IN' and m.source_material_id is null
			order by m.id limit 1
			""");

		JsonNode rows = content(api.get("/api/storage-units/" + entry.get("storage_unit_id")
			+ "/movement-history?material_id=" + entry.get("material_id")));

		JsonNode row = rows.valueStream().filter(r -> r.get("id").asLong() == ((Number) entry.get("id")).longValue())
			.findFirst().orElseThrow();
		assertThat(row.get("source").get("material_id").asLong()).isEqualTo(((Number) entry.get("material_id"))
			.longValue());
		assertThat(row.get("source").get("batch_code").asString()).isEqualTo(entry.get("batch_code"));
		assertThat(row.get("balance_after").decimalValue()).isEqualByComparingTo(row.get("quantity").decimalValue());
	}

	@Test
	void unknownTankIsNotFound() {
		assertThat(api.get("/api/storage-units/999999/movement-history/summary").status()).isEqualTo(404);
	}

	/** Sonraki islem bir sonraki saniyede. */
	private void tick() {
		clock = clock.plusSeconds(1);
		Timestamps.useClock(Clock.fixed(clock, ZoneOffset.UTC));
	}

	private long tank(Map<String, Object> flags) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", "Defter tanki");
		body.put("code", "LEDGER-" + codes.incrementAndGet());
		body.put("capacity", 5000);
		body.put("capacity_unit", "LITRE");
		body.putAll(flags);
		return id(api.post("/api/storage-units", body));
	}

	private Response open(long tank, long type, String quantity, String acidity) {
		tick();
		Response r = api.post("/api/storage-movements/opening", Map.of("storage_unit_id", tank,
			"production_product_type_id", type, "quantity", new BigDecimal(quantity), "unit", "LITRE",
			"attribute_values", List.of(Map.of("production_product_attribute_definition_id", ACIDITY,
				"value_decimal", new BigDecimal(acidity)))));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
		return r;
	}

	private static long materialOf(Response movement) {
		return data(movement).get("material_id").asLong();
	}

	private static JsonNode content(Response r) {
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		return r.json().get("content");
	}

	private static BigDecimal acidityAfter(JsonNode row) {
		return row.get("mix_after").valueStream()
			.filter(v -> v.get("definition_id").asInt() == ACIDITY)
			.map(v -> v.get("value").decimalValue()).findFirst().orElseThrow();
	}

	private BigDecimal acidity(long material) {
		return jdbc.queryForObject("select value_decimal from material_attribute_values "
			+ "where material_id = ? and production_product_attribute_definition_id = ?", BigDecimal.class, material,
			ACIDITY);
	}

}
