package com.olivamore.api.production;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.BatchFlow;
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
 * Teslimati gruplara bolme (docs/BATCH_GROUPS.md). Hat 1: 1 Kantarda Tartim, 2 Ayiklama, 3 Ikinci Tartim, 4 Transfer
 * teslimatta; 5 Sikim (ilk makineli) ve sonrasi grupta.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchGroupsIT {

	private final AtomicInteger codes = new AtomicInteger();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	BatchFlow flow;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		flow = new BatchFlow(api, jdbc);
	}

	@Test
	void deliveryHasOnlyThePreMachineStagesAndIsWeighedWithTheGivenKg() {
		long delivery = flow.weighedDelivery(code(), 1000);

		JsonNode batch = data(api.get("/api/production-batches/" + delivery));

		assertThat(stageStatuses(batch)).containsExactly("1:completed", "2:waiting", "3:waiting", "4:waiting");
		assertThat(batch.get("status").asString()).isEqualTo("in_progress");
		assertThat(oliveOf(delivery)).containsExactly(new BigDecimal("1000.0000"));
	}

	@Test
	void groupTakesItsPortionAndStartsFromTheChosenStage() {
		long delivery = flow.weighedDelivery(code(), 5000);
		flow.completeUpTo(delivery, 3);

		JsonNode group = data(split(delivery, Map.of("quantity_kg", 300, "start_stage_id", 2)));

		assertThat(group.get("parent_batch_id").asLong()).isEqualTo(delivery);
		assertThat(group.get("batch_code").asString()).endsWith("-1");
		assertThat(group.get("status").asString()).isEqualTo("pending");
		assertThat(stageStatuses(group)).startsWith("1:skipped", "2:waiting", "3:waiting", "4:waiting", "5:waiting")
			.hasSize(11);
		assertThat(jdbc.queryForObject("select inherited_from_process_id from batch_processes"
			+ " where production_batch_id = ? and production_stage_id = 1", Long.class, group.get("id").asLong()))
			.isEqualTo(processId(delivery, 1));
		assertThat(oliveOf(group.get("id").asLong())).containsExactly(new BigDecimal("300.0000"));
		assertThat(oliveOf(delivery)).containsExactly(new BigDecimal("4500.0000"));
	}

	@Test
	void withoutAStartTheGroupContinuesWhereTheDeliveryIs() {
		long delivery = flow.weighedDelivery(code(), 1000);

		JsonNode group = data(split(delivery, Map.of("quantity_kg", 400)));

		assertThat(stageStatuses(group)).startsWith("1:skipped", "2:waiting", "3:waiting");
		assertThat(oliveOf(delivery)).containsExactly(new BigDecimal("600.0000"));
	}

	@Test
	void partialSplitNeedsAWeighingButTheWholeRemainderCanAlwaysGo() {
		long delivery = flow.weighedDelivery(code(), 1000);
		flow.completeUpTo(delivery, 2);

		assertRule(split(delivery, Map.of("quantity_kg", 300)), "Kısmi ayırma yalnız bir tartımdan sonra yapılır; "
			+ "son tamamlanan aşama 'Ayıklama'. Önce tartın ya da kalanın hepsiyle grup açın.");
		JsonNode group = data(split(delivery, Map.of("all_remaining", true)));

		assertThat(group.get("quantity_kg").decimalValue()).isEqualByComparingTo("1000");
		assertThat(stageStatuses(group)).startsWith("1:skipped", "2:skipped", "3:waiting");
		assertThat(oliveOf(delivery)).isEmpty();
		assertThat(stageStatuses(data(api.get("/api/production-batches/" + delivery))))
			.as("zeytin kalmadi: teslimatin bekleyen asamalari islenmez")
			.containsExactly("1:completed", "2:completed", "3:skipped", "4:skipped");
	}

	@Test
	void theSplitCannotExceedTheRemainingOlives() {
		long delivery = flow.weighedDelivery(code(), 1000);
		split(delivery, Map.of("quantity_kg", 700));

		assertRule(split(delivery, Map.of("quantity_kg", 301)),
			"Ayrılacak kilo (301 kg) ayrılmamış zeytinden (300 kg) fazla olamaz.");
		assertThat(data(split(delivery, Map.of("quantity_kg", 300))).get("batch_code").asString()).endsWith("-2");
	}

	@Test
	void anUnweighedDeliveryCannotBeSplit() {
		long delivery = flow.delivery(code());

		assertRule(split(delivery, Map.of("all_remaining", true)),
			"Teslimatta ayrılacak zeytin yok; önce kantarda tartılmalı.");
	}

	@Test
	void startMustBeAStageTheDeliveryHasDone() {
		long delivery = flow.weighedDelivery(code(), 1000);

		Response r = split(delivery, Map.of("quantity_kg", 100, "start_stage_id", 3));

		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.body()).contains("start_stage_id");
	}

	@Test
	void aGroupIsNotSplitAgainAndKeepsTheDeliveryInfo() {
		long delivery = flow.weighedDelivery(code(), 1000);
		long group = id(split(delivery, Map.of("quantity_kg", 500)));

		assertRule(split(group, Map.of("all_remaining", true)), "bir grup; grup yeniden bölünmez.");
		Response change = api.patch("/api/production-batches/" + group, Map.of("truck_plate", "34 XX 1"));
		assertThat(change.status()).isEqualTo(422);
		assertThat(change.json().get("detail").asString()).contains("bir teslimatın grubu");

		api.patch("/api/production-batches/" + delivery, Map.of("truck_plate", "34 YY 2"));
		assertThat(data(api.get("/api/production-batches/" + group)).get("truck_plate").asString())
			.isEqualTo("34 YY 2");
	}

	@Test
	void theDeliveryIsCompletedWhenAllOlivesAreAllocatedAndEveryGroupIsDone() {
		long delivery = flow.weighedDelivery(code(), 1000);
		long first = id(split(delivery, Map.of("quantity_kg", 600)));
		long second = id(split(delivery, Map.of("all_remaining", true)));

		finishAllStages(first);
		assertThat(status(delivery)).isEqualTo("in_progress");
		finishAllStages(second);

		assertThat(status(second)).isEqualTo("completed");
		assertThat(status(delivery)).isEqualTo("completed");
	}

	@Test
	void aDeliveryWithGroupsIsNotDeleted() {
		long delivery = flow.weighedDelivery(code(), 1000);
		split(delivery, Map.of("quantity_kg", 500));

		Response r = api.delete("/api/production-batches/" + delivery);

		assertThat(r.status()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).contains("1 grup");
	}

	// ---- yardimcilar -------------------------------------------------------

	private String code() {
		return "T-GRUP-" + codes.incrementAndGet();
	}

	private Response split(long delivery, Map<String, Object> body) {
		return api.post("/api/production-batches/" + delivery + "/groups", new HashMap<>(body));
	}

	private static void assertRule(Response r, String detail) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).contains(detail);
	}

	/** "asama:durum", asama sirasinda. */
	private static List<String> stageStatuses(JsonNode batch) {
		List<String> out = new java.util.ArrayList<>();
		batch.get("processes").forEach(p -> out.add(p.get("production_stage_id").asLong() + ":"
			+ p.get("status").asString()));
		return out;
	}

	/** Grubun asamalari dogrudan kapanir; durum bir proses guncellemesiyle yeniden hesaplanir. */
	private void finishAllStages(long group) {
		jdbc.update("update batch_processes set status = 'completed', started_at = now(), completed_at = now()"
			+ " where production_batch_id = ? and status = 'waiting'", group);
		Response r = api.patch(flow.process(group, 5), Map.of("note", "bitti"));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	private String status(long batch) {
		return data(api.get("/api/production-batches/" + batch)).get("status").asString();
	}

	private long processId(long batch, int stage) {
		return jdbc.queryForObject("select id from batch_processes where production_batch_id = ? and production_stage_id = ?",
			Long.class, batch, stage);
	}

	private List<BigDecimal> oliveOf(long batch) {
		return jdbc.queryForList("select quantity from materials where production_batch_id = ? and status = 'active'"
			+ " and current_location_type = 'production_batch' order by id", BigDecimal.class, batch);
	}

}
