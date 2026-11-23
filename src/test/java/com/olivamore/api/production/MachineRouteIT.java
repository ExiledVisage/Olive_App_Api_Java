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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sikim'da makine adimlari (machine_route: Kirici, Malaksor, Dekantor, Separator): asama kronometresi icinde,
 * sirayla, parca ayni anda tek partide; olcumler tipin alanlarina gore, aralik disi kaydedilip isaretlenir. Testin
 * kendi Kirici ve Malaksor makineleri var (demo ya da baska testlerin acik adimlari karismasin).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MachineRouteIT {

	private static final int PRESSING = 5;

	private final AtomicInteger codes = new AtomicInteger();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	BatchFlow flow;

	long crusher;

	long malaxer;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		flow = new BatchFlow(api, jdbc);
		crusher = part(1, "KRC-RT", "Kırıcı Test");
		malaxer = part(2, "MLK-RT", "Malaksör Test");
	}

	@Test
	void stepsRunInOrderInsideTheStageTimer() {
		long batch = atPressing();
		String steps = flow.process(batch, PRESSING) + "/machine-runs/";

		JsonNode route = data(api.get(flow.process(batch, PRESSING) + "/machine-runs"));
		assertThat(route.get("total").asInt()).isEqualTo(4);
		assertThat(route.get("steps").valueStream().map(s -> s.get("machine_type_key").asString()))
			.containsExactly("kirici", "malaksor", "dekantor", "separator");
		assertThat(route.get("steps").get(1).get("fields").get(0).get("field_key").asString()).isEqualTo("sicaklik");

		assertRule(api.post(steps + "1/start", Map.of("machine_part_id", crusher)), "'Sıkım' aşamasında önce "
			+ "kronometreyi başlatın; makine adımları kronometre çalışırken başlar.");
		timer(batch, "start");
		assertRule(api.post(steps + "2/start", Map.of("machine_part_id", malaxer)),
			"'Malaksör' adımı, 'Kırıcı' adımı başlamadan başlatılamaz.");
		assertThat(api.post(steps + "1/start", Map.of()).status()).as("birden cok parca: secim zorunlu")
			.isEqualTo(422);
		ok(api.post(steps + "1/start", Map.of("machine_part_id", crusher)));
		ok(api.post(steps + "2/start", Map.of("machine_part_id", malaxer)));
		assertRule(api.post(flow.process(batch, PRESSING) + "/machine/finish", Map.of()),
			"'Sıkım' aşamasında 'Kırıcı' adımı açık; önce makine adımını bitirin.");

		JsonNode afterCrusher = data(ok(api.post(steps + "1/finish",
			Map.of("values", Map.of("devir", 2900, "hamur_sicakligi", 45)))));
		Response missing = api.post(steps + "2/finish", Map.of("values", Map.of("devir", 20, "basinc", 3)));
		JsonNode afterMalaxer = data(ok(api.post(steps + "2/finish",
			Map.of("values", Map.of("sicaklik", 27.5, "inert_gaz", true), "note", "ılık"))));

		JsonNode crusherRun = afterCrusher.get("steps").get(0).get("run");
		assertThat(afterCrusher.get("steps").get(0).get("status").asString()).isEqualTo("done");
		assertThat(crusherRun.get("values").valueStream().filter(v -> v.get("field_key").asString()
			.equals("hamur_sicakligi")).findFirst().orElseThrow().get("out_of_range").asBoolean()).isTrue();
		assertThat(missing.status()).isEqualTo(422);
		assertThat(missing.json().get("errors").has("values.sicaklik")).isTrue();
		assertThat(missing.json().get("errors").get("values.basinc").get(0).asString())
			.isEqualTo("Bu makine tipinde böyle bir ölçüm alanı yok.");
		assertThat(afterMalaxer.get("completed").asInt()).isEqualTo(2);
		assertThat(afterMalaxer.get("steps").get(1).get("run").get("note").asString()).isEqualTo("ılık");

		timer(batch, "finish");
		assertRule(api.post(steps + "3/start", Map.of()),
			"'Sıkım' aşamasında kronometre bitti; makine adımı başlatılamaz.");
		ok(flow.complete(batch, PRESSING, BatchFlow.pressing()));
		assertThat(api.post(steps + "5/start", Map.of()).status()).isEqualTo(404);
	}

	@Test
	void stepTimesAndValuesCanBeCorrectedLater() {
		long batch = atPressing();
		String steps = flow.process(batch, PRESSING) + "/machine-runs/";
		timer(batch, "start");
		ok(api.post(steps + "1/start", Map.of("machine_part_id", crusher)));
		ok(api.post(steps + "1/finish", Map.of("values", Map.of("devir", 2900))));

		Response backwards = api.patch(steps + "1", Map.of("finished_at", "2020-01-01T08:00:00"));
		JsonNode corrected = data(ok(api.patch(steps + "1", Map.of("started_at", "2026-11-20T09:00:00",
			"finished_at", "2026-11-20T09:40:00", "values", Map.of("elek_capi", 6)))));

		assertRule(backwards, "Makine adımının bitişi başlangıcından önce olamaz.");
		JsonNode run = corrected.get("steps").get(0).get("run");
		assertThat(run.get("started_at").asString()).startsWith("2026-11-20");
		assertThat(run.get("values").valueStream().map(v -> v.get("field_key").asString()))
			.containsExactly("elek_capi");
		timer(batch, "finish");
	}

	@Test
	void aPartWorksForOneBatchAtATimeAndReopeningTheStageClearsItsSteps() {
		long first = atPressing();
		long second = atPressing();
		timer(first, "start");
		timer(second, "start");
		ok(api.post(flow.process(first, PRESSING) + "/machine-runs/1/start", Map.of("machine_part_id", crusher)));

		Response busy = api.post(flow.process(second, PRESSING) + "/machine-runs/1/start",
			Map.of("machine_part_id", crusher));
		JsonNode secondRoute = data(api.get(flow.process(second, PRESSING) + "/machine-runs"));
		String firstCode = jdbc.queryForObject("select batch_code from production_batches where id = ?",
			String.class, first);

		assertRule(busy, "'Kırıcı Test' şu an " + firstCode + " partisinde çalışıyor; o adım bitince seçilebilir.");
		assertThat(secondRoute.get("steps").get(0).get("parts").valueStream()
			.filter(p -> p.get("id").asLong() == crusher).findFirst().orElseThrow()
			.get("busy_batch_code").asString()).isEqualTo(firstCode);

		ok(api.patch(flow.process(first, PRESSING), Map.of("status", "waiting")));

		JsonNode firstRoute = data(api.get(flow.process(first, PRESSING) + "/machine-runs"));
		assertThat(firstRoute.get("steps").get(0).get("status").asString()).isEqualTo("waiting");
		assertThat(firstRoute.get("timer_started").asBoolean()).isFalse();
		ok(api.post(flow.process(second, PRESSING) + "/machine-runs/1/start", Map.of("machine_part_id", crusher)));
		ok(api.post(flow.process(second, PRESSING) + "/machine-runs/1/finish", Map.of()));
	}

	@Test
	void stagesWithoutAMachineRouteHaveNoSteps() {
		long batch = atPressing();

		assertRule(api.get(flow.process(batch, 4) + "/machine-runs"),
			"'Sıkım Ünitesine Transfer' aşamasında makine hattı yok.");
	}

	/** Sikim'a kadar (dahil degil) tamamlanmis grup. */
	private long atPressing() {
		long batch = flow.batch("MR-" + codes.incrementAndGet() + "-" + System.nanoTime() % 100000);
		flow.completeUpTo(batch, 4);
		return batch;
	}

	private void timer(long batch, String action) {
		ok(api.post(flow.process(batch, PRESSING) + "/machine/" + action, Map.of()));
	}

	/** Tipe yeni makine ve onun tek parcasi. */
	private long part(int type, String code, String name) {
		JsonNode machine = data(api.post("/api/machines", Map.of("machine_type_id", type, "code", code, "name", name)));
		return machine.get("parts").get(0).get("id").asLong();
	}

	private static Response ok(Response response) {
		assertThat(response.status()).as(response.body()).isEqualTo(200);
		return response;
	}

	private static void assertRule(Response response, String message) {
		assertThat(response.status()).as(response.body()).isEqualTo(422);
		assertThat(response.json().get("detail").asString()).isEqualTo(message);
	}

}
