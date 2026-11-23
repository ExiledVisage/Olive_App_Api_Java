package com.olivamore.api.production;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.BatchFlow;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * On dinlendirme tanki (C3-C5), her test kendi tanklari ve partileriyle: asama tanka girince dinlenmede bekler, iptal
 * edilirse urun partiye doner; parti bir tanka bir kez girer; Boşalt girdileri kapatir, cekileni hedeflere oransal
 * dagitir, farki parti basina tortu yapar ve asamalari tamamlar; tolerans, tamami-ya-da-hic ve asamasiz (eski)
 * materyaller. Gemlik yaginin yogunlugu kategoriden 0.916; tortu Pirina (KG).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreSettlingRulesIT {

	private static final int GEMLIK_OIL = 3;
	private static final int AYVALIK_OIL = 4;
	private static final int POMACE = 3;

	private final AtomicInteger codes = new AtomicInteger();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StockOperations stock;

	Api api;

	BatchFlow flow;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		OilInLitres.apply(jdbc);
		flow = new BatchFlow(api, jdbc);
	}

	@Test
	void restingStageHoldsTheBatchUntilCancelledAndThePortionReturnsToTheBatch() {
		long tank = tank("pre_settling");
		long batch = batchUpToAnalysis();
		long oil = oil(batch);

		flow.rest(batch, tank, "500");

		assertThat(batchStatus(batch)).isEqualTo("in_progress");
		assertThat(quantity(oil)).isEqualByComparingTo("400.25");
		assertThat(tankMaterials(tank)).hasSize(1);
		assertRule(api.patch(flow.process(batch, 10), Map.of("status", "in_progress")),
			"'Filtreleme' aşamasına geçilemez; önceki aşamalar tamamlanmadı: Ön Dinlenme.");
		assertRule(api.patch(flow.process(batch, 9), Map.of("status", "completed")),
			"'Ön Dinlenme' aşaması dinlenmede; ön dinlendirme tankı boşaltılınca tamamlanır. Yanlış aktarımsa aşama "
				+ "iptal edilir (waiting).");

		Response cancelled = api.patch(flow.process(batch, 9), Map.of("status", "waiting"));

		assertThat(data(cancelled).get("status").asString()).isEqualTo("waiting");
		assertThat(tankMaterials(tank)).isEmpty();
		assertThat(quantity(oil)).isEqualByComparingTo(BatchFlow.OIL);
		assertThat(activeOil(batch)).containsExactly(oil);
		assertThat(balance(tank)).isZero();

		flow.rest(batch, tank, BatchFlow.OIL);
		assertThat(tankMaterials(tank)).containsExactly(oil);
	}

	@Test
	void aBatchEntersAPreSettlingTankOnceAndOnlyAPreSettlingTank() {
		long tank = tank("pre_settling");
		long last = tank("final");
		long batch = batchUpToAnalysis();
		long process = processId(batch, 9);

		assertRule(flow.complete(batch, 9, Map.of("quantity", BatchFlow.OIL, "storage_unit_id", last)),
			"'" + code(last) + "' bir son tank; ön dinlendirme aşaması yalnızca ön dinlendirme tankına giriş yapar.");
		flow.rest(batch, tank, "500");

		assertThatThrownBy(() -> stock.enterPreSettling(tank, oil(batch), BigDecimal.TEN, process, null))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessage("'" + batchCode(batch) + "' partisi bu ön dinlendirme tankına zaten girdi. Miktar yanlışsa "
				+ "aşamayı iptal edip yeniden gönderin.");
	}

	@Test
	void emptyingClosesTheEntriesSplitsTheWithdrawnAmountAndOpensResiduePerBatch() {
		long tank = tank("pre_settling");
		long first = tank("final");
		long second = tank("final");
		long a = batchUpToAnalysis();
		long b = batchUpToAnalysis();
		long oilA = oil(a);
		long oilB = oil(b);
		flow.rest(a, tank, BatchFlow.OIL);
		flow.rest(b, tank, BatchFlow.OIL);
		flow.startFiltering(tank);

		// 1800.5 L girdi, 1700 L cekildi: parti basina 850 L (hedeflere 1000:700 oraninda), 50.25 L fark
		JsonNode emptied = data(empty(tank, Map.of(first, "1000", second, "700")));

		assertThat(emptied.get("out_movements")).hasSize(4);
		assertThat(emptied.get("in_movements")).hasSize(4);
		assertThat(status(oilA)).isEqualTo("consumed");
		assertThat(status(oilB)).isEqualTo("consumed");
		assertThat(balance(tank)).isZero();
		assertThat(balance(first)).isEqualByComparingTo("1000");
		assertThat(balance(second)).isEqualByComparingTo("700");
		assertThat(withdrawn(tank, oilA)).isEqualByComparingTo("850");
		for (long batch : List.of(a, b)) {
			assertThat(stageStatus(batch, 9)).isEqualTo("completed");
			// 50.25 L x 0.916 kg/L
			assertThat(residueKg(batch)).isEqualByComparingTo("46.029");
			Map<String, Object> residue = jdbc.queryForMap("select m.quantity, m.unit, m.parent_material_id,"
				+ " t.production_product_id from materials m join production_product_types t"
				+ " on t.id = m.production_product_type_id where m.parent_material_id = ?", batch == a ? oilA : oilB);
			assertThat((BigDecimal) residue.get("quantity")).isEqualByComparingTo("46.029");
			assertThat(residue.get("unit")).isEqualTo("KG");
			assertThat(((Number) residue.get("production_product_id")).intValue()).isEqualTo(POMACE);
		}
		assertThat(emptied.get("residue_materials")).hasSize(2);
		assertThat(jdbc.queryForObject("select closed_by_batch_process_id from materials where id = ?", Long.class,
			oilA)).isEqualTo(processId(a, 9));
	}

	@Test
	void withdrawingMoreThanTheTankHeldIsRejectedBeyondTheTolerance() {
		long tank = tank("pre_settling");
		long last = tank("final");
		long batch = batchUpToAnalysis();
		flow.rest(batch, tank, BatchFlow.OIL);
		flow.startFiltering(tank);

		assertRule(empty(tank, Map.of(last, "905")), "Çekilen miktar (905 LITRE) tanktakinden (900.25 LITRE) fazla; "
			+ "fark ölçüm toleransını (%0.5) aşıyor.");
		assertThat(stageStatus(batch, 9)).isEqualTo("resting");

		// %0.42 fazla: olcum farki, girenin tamami cekilmis sayilir ve tortu acilmaz
		assertThat(empty(tank, Map.of(last, "904")).status()).isEqualTo(200);
		assertThat(balance(last)).isEqualByComparingTo(BatchFlow.OIL);
		assertThat(residueKg(batch)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from materials where parent_material_id = ?", Long.class,
			oil(batch))).isZero();
	}

	@Test
	void filteringStartsFromTheTankAndEndsWithTheEmptying() {
		long tank = tank("pre_settling");
		long last = tank("final");
		long a = batchUpToAnalysis();
		long b = batchUpToAnalysis();
		long late = batchUpToAnalysis();
		flow.rest(a, tank, "400");
		flow.rest(b, tank, "400");

		JsonNode resting = data(api.get("/api/storage-units/" + tank + "/resting-batches"));
		assertThat(resting).hasSize(2);
		assertThat(resting.get(0).get("batch_code").asString()).isEqualTo(batchCode(a));
		assertThat(resting.get(0).get("filtering_process_id").asLong()).isEqualTo(processId(a, 10));
		assertThat(resting.get(0).get("filtering_started_at").isNull()).isTrue();
		assertRule(api.post(flow.process(a, 10) + "/machine/start", Map.of()), "'Filtreleme' ön dinlendirme tankında "
			+ "yapılır; kronometre tank sayfasından başlatılır, Boşalt ile biter.");
		assertRule(empty(tank, Map.of(last, "700")), "'Filtreleme' başlatılmadı; tank boşaltılmadan önce tank "
			+ "sayfasından filtrelemeyi başlatın.");

		JsonNode started = data(api.post("/api/storage-units/" + tank + "/filtering/start", Map.of()));
		assertThat(started.get(1).get("filtering_started_at").isNull()).isFalse();
		assertThat(stageStatus(a, 10)).isEqualTo("in_progress");
		assertRule(api.post("/api/storage-units/" + tank + "/filtering/start", Map.of()),
			"Bu tankta filtreleme zaten başladı; Boşalt ile biter.");
		assertRule(flow.complete(late, 9, Map.of("quantity", "400", "storage_unit_id", tank)),
			"Bu tankta filtreleme sürüyor; tanka giriş Boşalt'tan sonra yapılabilir.");

		// Yanlis aktarim iptal: o grubun filtrelemesi geri alinir, digerininki surer
		assertThat(api.patch(flow.process(a, 9), Map.of("status", "waiting")).status()).isEqualTo(200);
		assertThat(stageStatus(a, 10)).isEqualTo("waiting");

		assertThat(empty(tank, Map.of(last, "390")).status()).isEqualTo(200);
		assertThat(stageStatus(b, 9)).isEqualTo("completed");
		assertThat(stageStatus(b, 10)).isEqualTo("completed");
		assertThat(jdbc.queryForObject("select machine_finished_at is not null from batch_processes where id = ?",
			Boolean.class, processId(b, 10))).isTrue();
		assertThat(stageStatus(a, 10)).isEqualTo("waiting");
	}

	@Test
	void yieldIsTheFilteredOilOverThePressedOlive() {
		long tank = tank("pre_settling");
		long last = tank("final");
		long batch = batchUpToAnalysis();
		flow.rest(batch, tank, BatchFlow.OIL);
		String yield = flow.process(batch, 12) + "/yield";

		JsonNode before = data(api.get(yield));
		assertThat(before.get("olive_kg").decimalValue()).isEqualByComparingTo("4800");
		assertThat(before.get("filtered_oil_kg").isNull()).isTrue();
		assertThat(before.get("yield_percent").isNull()).isTrue();

		flow.startFiltering(tank);
		assertThat(empty(tank, Map.of(last, "890")).status()).isEqualTo(200);

		// 890 L x 0.916 = 815.24 kg; 815.24 / 4800 = %16.98
		JsonNode after = data(api.get(yield));
		assertThat(after.get("filtered_oil_kg").decimalValue()).isEqualByComparingTo("815.24");
		assertThat(after.get("yield_percent").decimalValue()).isEqualByComparingTo("16.98");

		JsonNode sent = data(flow.complete(batch, 12, Map.of("yield_percent", 17)));
		assertThat(sent.get("status").asString()).isEqualTo("awaiting_approval");
		JsonNode stored = sent.get("operation_data");
		assertThat(stored.get("yield_percent").decimalValue()).isEqualByComparingTo("17");
		assertThat(stored.get("calculated_yield_percent").decimalValue()).isEqualByComparingTo("16.98");
		assertThat(stored.get("deviation_ratio").decimalValue()).isEqualByComparingTo("0.0012");
		assertRule(flow.complete(batchUpToAnalysis(), 12, Map.of("yield_percent", 17)),
			"'Randıman Tespiti' aşamasına geçilemez; önceki aşamalar tamamlanmadı: Ön Dinlenme, Filtreleme.");
	}

	@Test
	void emptyingIsAllOrNothing() {
		long tank = tank("pre_settling");
		long accepting = tank("final");
		long ayvalikOnly = tank("final");
		Response allowed = api.put("/api/storage-units/" + ayvalikOnly + "/allowed-production-product-types",
			Map.of("production_product_type_ids", List.of(AYVALIK_OIL)));
		assertThat(allowed.status()).as(allowed.body()).isEqualTo(200);
		long batch = batchUpToAnalysis();
		flow.rest(batch, tank, BatchFlow.OIL);
		flow.startFiltering(tank);

		assertRule(empty(tank, Map.of(accepting, "400", ayvalikOnly, "400")),
			"Seçilen tank bu spesifik ürünü kabul etmiyor.");
		assertRule(api.post("/api/storage-units/" + accepting + "/empty", Map.of("targets",
			List.of(Map.of("storage_unit_id", tank, "quantity", 1)))),
			"'" + code(accepting) + "' bir son tank; Boşalt yalnızca ön dinlendirme tankında yapılır.");

		assertThat(stageStatus(batch, 9)).isEqualTo("resting");
		assertThat(balance(tank)).isEqualByComparingTo(BatchFlow.OIL);
		assertThat(balance(accepting)).isZero();
	}

	@Test
	void materialThatEnteredWithoutAStageClosesWithoutResidue() {
		long tank = tank("pre_settling");
		long last = tank("final");
		long legacy = jdbc.queryForObject("insert into materials (production_batch_id, production_product_type_id,"
			+ " origin_type, quantity, unit, status, current_location_type, current_location_id, created_at, updated_at)"
			+ " select id, ?, 'BATCH_PROCESS', 100, 'LITRE', 'active', 'storage_unit', ?, now(), now()"
			+ " from production_batches order by id limit 1 returning id", Long.class, GEMLIK_OIL, tank);
		long batch = batchUpToAnalysis();
		flow.rest(batch, tank, BatchFlow.OIL);
		flow.startFiltering(tank);

		// 999.75 L girdi, 900 L cekildi: payi 100 / 999.75 olan eski materyalin farki tortu olmaz
		assertThat(empty(tank, Map.of(last, "900")).status()).isEqualTo(200);

		assertThat(status(legacy)).isEqualTo("consumed");
		assertThat(jdbc.queryForObject("select count(*) from materials where parent_material_id = ?", Long.class,
			legacy)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from materials where parent_material_id = ?", Long.class,
			oil(batch))).isEqualTo(1);
		assertThat(stageStatus(batch, 9)).isEqualTo("completed");
		assertThat(balance(last)).isEqualByComparingTo("900");
	}

	// ---- yardimcilar -------------------------------------------------------

	private long batchUpToAnalysis() {
		long batch = flow.batch("T-SETTLE-" + codes.incrementAndGet());
		flow.completeUpTo(batch, 8);
		return batch;
	}

	private long tank(String kind) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", "Dinlenme kurali");
		body.put("code", "SETTLE-" + codes.incrementAndGet());
		body.put("capacity", 5000);
		body.put("capacity_unit", "LITRE");
		body.put("kind", kind);
		return id(api.post("/api/storage-units", body));
	}

	private Response empty(long tank, Map<Long, String> targets) {
		List<Map<String, Object>> rows = targets.entrySet().stream().sorted(Map.Entry.comparingByKey())
			.map(e -> Map.<String, Object>of("storage_unit_id", e.getKey(), "quantity", new BigDecimal(e.getValue())))
			.toList();
		return api.post("/api/storage-units/" + tank + "/empty", Map.of("targets", rows));
	}

	/** Partinin (analizli) yag materyali. */
	private long oil(long batch) {
		return jdbc.queryForObject("select id from materials where production_batch_id = ? and production_product_type_id"
			+ " = ? and parent_material_id is not null order by id limit 1", Long.class, batch, GEMLIK_OIL);
	}

	private List<Long> activeOil(long batch) {
		return jdbc.queryForList("select id from materials where production_batch_id = ? and production_product_type_id"
			+ " = ? and status = 'active' order by id", Long.class, batch, GEMLIK_OIL);
	}

	private List<Long> tankMaterials(long tank) {
		return jdbc.queryForList("select id from materials where current_location_type = 'storage_unit'"
			+ " and current_location_id = ? and status = 'active' order by id", Long.class, tank);
	}

	private long processId(long batch, int stage) {
		return jdbc.queryForObject("select id from batch_processes where production_batch_id = ? and"
			+ " production_stage_id = ?", Long.class, batch, stage);
	}

	private String stageStatus(long batch, int stage) {
		return jdbc.queryForObject("select status from batch_processes where id = ?", String.class,
			processId(batch, stage));
	}

	private BigDecimal residueKg(long batch) {
		return jdbc.queryForObject("select (operation_data ->> 'residue_kg')::numeric from batch_processes where id = ?",
			BigDecimal.class, processId(batch, 9));
	}

	/** Bosaltmada materyalden cekilen toplam (cikis hareketleri). */
	private BigDecimal withdrawn(long tank, long material) {
		return jdbc.queryForObject("select sum(quantity) from storage_movements where storage_unit_id = ? and"
			+ " material_id = ? and movement_type = 'TRANSFER_OUT'", BigDecimal.class, tank, material);
	}

	private String batchStatus(long batch) {
		return jdbc.queryForObject("select status from production_batches where id = ?", String.class, batch);
	}

	private String batchCode(long batch) {
		return jdbc.queryForObject("select batch_code from production_batches where id = ?", String.class, batch);
	}

	private String code(long tank) {
		return jdbc.queryForObject("select code from storage_units where id = ?", String.class, tank);
	}

	private BigDecimal quantity(long material) {
		return jdbc.queryForObject("select quantity from materials where id = ?", BigDecimal.class, material);
	}

	private String status(long material) {
		return jdbc.queryForObject("select status from materials where id = ?", String.class, material);
	}

	private BigDecimal balance(long tank) {
		return jdbc.queryForObject("select coalesce(sum(current_quantity), 0) from storage_unit_balances"
			+ " where storage_unit_id = ?", BigDecimal.class, tank);
	}

	private static void assertRule(Response r, String detail) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).isEqualTo(detail);
	}

}
