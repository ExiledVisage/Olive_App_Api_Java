package com.olivamore.api.machine;

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

import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Makine sekmesi: durum (su an calisan parca ve partisi), calismalar listesi, olcum serisi ve donem ozeti. Test kendi
 * iki parcali kiricisini acar ve uc partiyi Sikim'da ondan gecirir: A parcasinda biri biten biri acik, B parcasinda
 * biten (hamur sicakligi aralik disi).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MachineActivityIT {

	private static final int CRUSHER = 1;
	private static final int PRESSING = 5;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	BatchFlow flow;

	long machine;

	long partA;

	long partB;

	String pressedCode;

	String runningCode;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		flow = new BatchFlow(api, jdbc);
		JsonNode created = data(api.post("/api/machines", Map.of("machine_type_id", CRUSHER, "code", "KRC-AT",
			"name", "Kırıcı Takip", "parts", List.of(Map.of("code", "A", "name", "Hat A"),
				Map.of("code", "B", "name", "Hat B")))));
		machine = created.get("id").asLong();
		partA = created.get("parts").get(0).get("id").asLong();
		partB = created.get("parts").get(1).get("id").asLong();

		long pressed = crush(partA, Map.of("devir", 2900, "hamur_sicakligi", 24));
		ok(api.post(flow.process(pressed, PRESSING) + "/machine/finish", Map.of()));
		ok(flow.complete(pressed, PRESSING, BatchFlow.pressing()));
		crush(partB, Map.of("devir", 3000, "hamur_sicakligi", 45));
		long running = atPressing();
		ok(api.post(flow.process(running, PRESSING) + "/machine-runs/1/start", Map.of("machine_part_id", partA)));
		pressedCode = code(pressed);
		runningCode = code(running);
	}

	@Test
	void machineShowsWhichBatchItIsRunning() {
		JsonNode activity = data(api.get("/api/machines/" + machine)).get("activity");
		JsonNode listed = data(api.get("/api/machines?machine_type_id=" + CRUSHER)).valueStream()
			.filter(m -> m.get("id").asLong() == machine).findFirst().orElseThrow().get("activity");

		assertThat(activity.get("status").asString()).isEqualTo("running");
		assertThat(activity.get("running")).hasSize(1);
		assertThat(activity.get("running").get(0).get("batch_code").asString()).isEqualTo(runningCode);
		assertThat(activity.get("running").get(0).get("part").get("name").asString()).isEqualTo("Hat A");
		assertThat(activity.get("last_run_at").isNull()).isFalse();
		assertThat(listed).isEqualTo(activity);
	}

	@Test
	void runsAreListedNewestFirstAndCanBePaged() {
		JsonNode all = api.get("/api/machines/" + machine + "/runs").json().get("content");
		JsonNode page = api.get("/api/machines/" + machine + "/runs?page=0&size=2").json();
		JsonNode none = api.get("/api/machines/" + machine + "/runs?date_from=2020-01-01&date_to=2020-01-31").json();

		assertThat(all).hasSize(3);
		assertThat(all.get(0).get("batch_code").asString()).isEqualTo(runningCode);
		assertThat(all.get(0).get("finished_at").isNull()).isTrue();
		assertThat(all.get(2).get("batch_code").asString()).isEqualTo(pressedCode);
		assertThat(all.get(2).get("duration_minutes").isNull()).isFalse();
		assertThat(all.get(2).get("values").valueStream().map(v -> v.get("field_key").asString()))
			.containsExactly("devir", "hamur_sicakligi");
		assertThat(page.get("content")).hasSize(2);
		assertThat(page.get("page").get("total_elements").asInt()).isEqualTo(3);
		assertThat(none.get("content")).isEmpty();
	}

	@Test
	void seriesGivesTheFieldPerPart() {
		JsonNode series = data(api.get("/api/machines/" + machine + "/series?field=hamur_sicakligi&period=2d"));
		Response machineInfo = api.get("/api/machines/" + machine + "/series?field=kirici_tipi");
		Response unknown = api.get("/api/machines/" + machine + "/series?field=basinc");

		assertThat(series.get("field").get("unit").asString()).isEqualTo("°C");
		JsonNode a = series.get("parts").get(0);
		JsonNode b = series.get("parts").get(1);
		assertThat(a.get("part").get("name").asString()).isEqualTo("Hat A");
		assertThat(a.get("points")).hasSize(1);
		assertThat(a.get("points").get(0).get("value").decimalValue()).isEqualByComparingTo("24");
		assertThat(a.get("points").get(0).get("out_of_range").asBoolean()).isFalse();
		assertThat(b.get("points").get(0).get("value").decimalValue()).isEqualByComparingTo("45");
		assertThat(b.get("points").get(0).get("out_of_range").asBoolean()).isTrue();
		assertThat(machineInfo.status()).isEqualTo(422);
		assertThat(unknown.status()).isEqualTo(422);
	}

	@Test
	void summaryCountsRunsBatchesAndFieldStatistics() {
		JsonNode summary = data(api.get("/api/machines/" + machine + "/summary?period=1w"));
		JsonNode past = data(api.get("/api/machines/" + machine + "/summary?date_from=2020-01-01&date_to=2020-01-02"));
		Response badPeriod = api.get("/api/machines/" + machine + "/summary?period=5y");

		assertThat(summary.get("run_count").asInt()).isEqualTo(3);
		assertThat(summary.get("batch_count").asInt()).isEqualTo(3);
		assertThat(summary.get("pressed_kg").decimalValue()).isEqualByComparingTo("4800");
		assertThat(summary.get("utilisation_percent").isNull()).isFalse();
		assertThat(summary.get("running").get(0).get("batch_code").asString()).isEqualTo(runningCode);
		assertThat(summary.get("last_run").get("batch_code").asString()).isEqualTo(runningCode);
		JsonNode dough = summary.get("fields").valueStream()
			.filter(f -> f.get("field").get("field_key").asString().equals("hamur_sicakligi")).findFirst()
			.orElseThrow();
		assertThat(dough.get("parts")).hasSize(2);
		assertThat(dough.get("parts").get(0).get("average").decimalValue()).isEqualByComparingTo("24");
		assertThat(dough.get("parts").get(1).get("max").decimalValue()).isEqualByComparingTo("45");
		assertThat(past.get("run_count").asInt()).isZero();
		assertThat(past.get("pressed_kg").isNull()).isTrue();
		assertThat(badPeriod.status()).isEqualTo(422);
	}

	/** Sikim kronometresi icinde kirici adimini bu parcada calistirip olcumlerle bitirir. */
	private long crush(long part, Map<String, Object> values) {
		long batch = atPressing();
		String steps = flow.process(batch, PRESSING) + "/machine-runs/1/";
		ok(api.post(steps + "start", Map.of("machine_part_id", part)));
		ok(api.post(steps + "finish", Map.of("values", values)));
		return batch;
	}

	/** Sikim'a kadar tamamlanmis, kronometresi baslamis grup. */
	private long atPressing() {
		long batch = flow.batch("MA-" + System.nanoTime() % 1000000);
		flow.completeUpTo(batch, 4);
		ok(api.post(flow.process(batch, PRESSING) + "/machine/start", Map.of()));
		return batch;
	}

	private String code(long batch) {
		return jdbc.queryForObject("select batch_code from production_batches where id = ?", String.class, batch);
	}

	private static Response ok(Response response) {
		assertThat(response.status()).as(response.body()).isEqualTo(200);
		return response;
	}

}
