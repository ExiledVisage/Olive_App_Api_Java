package com.olivamore.api.quality;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kalite (docs/QUALITY.md): yag sinifi (on izleme, analiz asamasinda hesap ve elle sinif, demo veri, karsilastirma),
 * dolum kontrolu, geri cagirma ve tatbikat (ileri iz, siparis satirlari, kapanis), dokuman kaydi, hazirlik paneli,
 * yetkiler.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QualityIT {

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
	void gradePreviewFollowsTheCodexLimits() {
		JsonNode extra = grade(Map.of("acidity", 0.3, "peroxide", 8, "k232", 2.1, "k270", 0.15, "delta_k", 0.004,
			"defect_median", 0, "fruity_median", 3.5));
		assertThat(extra.get("grade").asString()).isEqualTo("extra_virgin");
		assertThat(extra.get("failures")).isEmpty();
		assertThat(extra.get("missing")).extracting(m -> m.get("key").asString()).containsExactly("faee");

		JsonNode virgin = grade(Map.of("acidity", 0.5, "k232", 2.55));
		assertThat(virgin.get("grade").asString()).isEqualTo("virgin");
		assertThat(virgin.get("label").asString()).isEqualTo("Natürel birinci");
		assertThat(virgin.get("failures").get(0).get("key").asString()).isEqualTo("k232");
		assertThat(virgin.get("failures").get(0).get("limit").asString()).isEqualTo("≤ 2,50");

		assertThat(grade(Map.of("acidity", 2.4)).get("grade").asString()).isEqualTo("lampante");
		assertThat(grade(Map.of("acidity", 0.3, "defect_median", 4)).get("grade").asString()).isEqualTo("lampante");
		assertThat(grade(Map.of("acidity", 0.3, "fruity_median", 0)).get("grade").asString()).isEqualTo("lampante");
		assertThat(grade(Map.of("k232", 2.1)).get("grade").isNull()).isTrue();
	}

	@Test
	void analysisWritesTheOilClassUnlessGivenByHand() {
		long computed = flow.batch("KAL-" + System.nanoTime());
		flow.completeUpTo(computed, 5);
		Response r = flow.complete(computed, 8, Map.of("attributes", List.of(
			Map.of("attribute_key", "acidity", "value", "0.3"), Map.of("attribute_key", "k232", "value", "2.55"),
			Map.of("attribute_key", "oil_class", "value", ""))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(oilClass(computed)).isEqualTo("virgin");

		long manual = flow.batch("KAL-" + System.nanoTime());
		flow.completeUpTo(manual, 5);
		r = flow.complete(manual, 8, Map.of("attributes", List.of(Map.of("attribute_key", "acidity", "value", "0.3"),
			Map.of("attribute_key", "oil_class", "value", "lampante"))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(oilClass(manual)).isEqualTo("lampante");
	}

	@Test
	void demoOilHasLaboratoryValuesAndTheCompareShowsThem() {
		List<String> classes = jdbc.queryForList("""
			select v.value_text from material_attribute_values v
			join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
			join attribute_definitions a on a.id = d.attribute_definition_id
			where a.attribute_key = 'oil_class'
			""", String.class);
		assertThat(classes).contains("extra_virgin", "virgin");

		LocalDate last = jdbc.queryForObject("select max(received_at)::date from production_batches", LocalDate.class);
		String range = "from=" + last.minusMonths(6).withDayOfMonth(1) + "&to=" + last;
		String ids = String.join(",", jdbc.queryForList("""
			select supplier_id::text from production_batches where parent_batch_id is null and supplier_id is not null
				and received_at >= ?::date
			group by supplier_id order by sum(quantity_kg) desc limit 2
			""", String.class, last.minusMonths(6).withDayOfMonth(1)));
		JsonNode values = data(api.get("/api/analytics/compare/supplier?ids=" + ids + "&metrics=extra_virgin_pct,"
			+ "avg_peroxide,avg_k232&" + range)).get("items").get(0).get("values");
		assertThat(values.get("extra_virgin_pct").asDouble()).isBetween(1.0, 100.0);
		assertThat(values.get("avg_peroxide").asDouble()).isPositive();
		assertThat(values.get("avg_k232").asDouble()).isPositive();
	}

	@Test
	void fillingChecksNeedTheSetupAndAreJudgedByTheServer() {
		long run = jdbc.queryForObject("""
			select r.id from finished_product_runs r where r.status = 'completed'
				and not exists (select 1 from filling_setups s where s.finished_product_run_id = r.id)
			order by r.id desc limit 1
			""", Long.class);
		String path = "/api/finished-product-runs/" + run;
		JsonNode before = data(api.get(path + "/filling-checks"));
		double target = before.get("summary").get("target_g").asDouble();
		assertThat(target).isPositive();

		assertThat(api.post(path + "/filling-checks", Map.of("weights", List.of(target))).status()).isEqualTo(422);
		Response missing = api.put(path + "/filling-setup", Map.of("conveyor_clean", true, "air_checked", true));
		assertThat(missing.json().get("errors").has("previous_product_cleared")).isTrue();
		JsonNode setup = data(api.put(path + "/filling-setup", Map.of("conveyor_clean", true, "air_checked", true,
			"previous_product_cleared", true))).get("setup");
		assertThat(setup.get("approved_by").get("name").asString()).isEqualTo("Olivamore Admin");

		JsonNode ok = data(api.post(path + "/filling-checks", Map.of("weights", List.of(target - 1, target + 1,
			target), "seal_ok", true, "label_ok", true)));
		JsonNode first = ok.get("checks").get(0);
		assertThat(first.get("weight_ok").asBoolean()).isTrue();
		assertThat(first.get("ok").asBoolean()).isTrue();
		assertThat(first.get("cap_ok").isNull()).isTrue();

		JsonNode off = data(api.post(path + "/filling-checks", Map.of("weights", List.of(target + 50),
			"coding_ok", false)));
		assertThat(off.get("checks").get(1).get("weight_ok").asBoolean()).isFalse();
		assertThat(off.get("summary").get("failed_count").asInt()).isEqualTo(1);
		assertThat(api.post(path + "/filling-checks", Map.of("weights", List.of(target), "glass_broken", true))
			.json().get("errors").has("note")).isTrue();
		assertThat(api.post(path + "/filling-checks", Map.of("weights", List.of(1, 2, 3, 4, 5, 6))).status())
			.isEqualTo(422);

		long planned = jdbc.queryForObject("select id from finished_product_runs where status = 'planned' limit 1",
			Long.class);
		assertThat(api.put("/api/finished-product-runs/" + planned + "/filling-setup", Map.of("conveyor_clean", true,
			"air_checked", true, "previous_product_cleared", true)).status()).isEqualTo(422);

		long checkId = off.get("checks").get(1).get("id").asLong();
		assertThat(api.as("depo@olivamore.com").delete("/api/filling-checks/" + checkId).status()).isEqualTo(403);
		assertThat(data(api.delete("/api/filling-checks/" + checkId)).get("checks")).hasSize(1);
	}

	@Test
	void recallFromAFillingFindsItsOrdersAndTracksTheCalls() {
		long run = jdbc.queryForObject("""
			select r.id from finished_product_runs r
			join finished_product_stock_movements i on i.finished_product_run_id = r.id and i.movement_type = 'stock_in'
			where exists (select 1 from finished_product_stock_movements p
				where p.finished_product_storage_slot_id = i.finished_product_storage_slot_id
					and p.finished_product_id = i.finished_product_id and p.movement_type = 'order_pick'
					and (p.moved_at, p.id) > (i.moved_at, i.id))
			order by r.id limit 1
			""", Long.class);
		JsonNode detail = data(api.post("/api/quality/recalls", Map.of("kind", "recall", "source_type", "run",
			"source_id", run, "reason", "Müşteri şikayeti: tortu")));
		JsonNode recall = detail.get("recall");
		assertThat(recall.get("code").asString()).startsWith("GC-");
		assertThat(recall.get("status").asString()).isEqualTo("open");
		assertThat(recall.get("summary").get("run_count").asInt()).isEqualTo(1);
		assertThat(detail.get("orders")).isNotEmpty();
		assertThat(detail.get("trace").get("runs").get(0).get("id").asLong()).isEqualTo(run);
		for (JsonNode order : detail.get("orders")) {
			assertThat(order.get("certainty").asString()).isIn("definite", "possible");
			assertThat(order.get("customer").asString()).isNotBlank();
		}

		long line = detail.get("orders").get(0).get("id").asLong();
		int quantity = detail.get("orders").get(0).get("quantity").asInt();
		assertThat(api.patch("/api/quality/recall-orders/" + line, Map.of("returned_quantity", quantity + 1))
			.status()).isEqualTo(422);
		JsonNode called = data(api.patch("/api/quality/recall-orders/" + line, Map.of("contacted", true,
			"returned_quantity", quantity, "note", "Arandı, iade alındı")));
		assertThat(called.get("recall").get("summary").get("contacted_orders").asInt()).isEqualTo(1);
		long id = recall.get("id").asLong();
		JsonNode retraced = data(api.post("/api/quality/recalls/" + id + "/retrace", Map.of()));
		assertThat(retraced.get("orders")).anySatisfy(o -> assertThat(o.get("contacted_at").isNull()).isFalse());

		JsonNode done = data(api.post("/api/quality/recalls/" + id + "/complete", Map.of("findings", "Kapatıldı")));
		assertThat(done.get("recall").get("status").asString()).isEqualTo("completed");
		assertThat(done.get("recall").get("result").isNull()).isTrue();
		assertThat(api.post("/api/quality/recalls/" + id + "/retrace", Map.of()).status()).isEqualTo(422);
	}

	@Test
	void deliveryTraceReachesTanksAndFillingsAndDrillPassesInTime() {
		long delivery = jdbc.queryForObject("""
			select b.id from production_batches b
			join materials m on m.production_batch_id in (select id from production_batches c
				where c.id = b.id or c.parent_batch_id = b.id)
			join storage_movements e on e.source_material_id = m.id
			join storage_movements o on o.material_id = e.material_id and o.finished_product_run_id is not null
				and o.moved_at >= e.moved_at
			where b.parent_batch_id is null order by b.id limit 1
			""", Long.class);
		JsonNode detail = data(api.post("/api/quality/recalls", Map.of("kind", "drill", "source_type", "delivery",
			"source_id", delivery, "reason", "Altı aylık izlenebilirlik tatbikatı")));
		JsonNode trace = detail.get("trace");
		assertThat(trace.get("batches")).isNotEmpty();
		assertThat(trace.get("tanks")).isNotEmpty();
		assertThat(trace.get("runs")).isNotEmpty();
		assertThat(detail.get("recall").get("code").asString()).startsWith("IT-");
		assertThat(detail.get("recall").get("target_minutes").asInt()).isEqualTo(240);

		long id = detail.get("recall").get("id").asLong();
		JsonNode done = data(api.post("/api/quality/recalls/" + id + "/complete", Map.of()));
		assertThat(done.get("recall").get("result").asString()).isEqualTo("passed");
		JsonNode panel = data(api.get("/api/quality/readiness"));
		assertThat(panel.get("drill").get("state").asString()).isEqualTo("ok");

		List<String> sources = new ArrayList<>();
		for (String type : List.of("delivery", "tank_material", "run")) {
			JsonNode options = data(api.get("/api/quality/recall-sources?type=" + type));
			assertThat(options).as(type).isNotEmpty();
			sources.add(options.get(0).get("name").asString());
		}
		assertThat(sources).allSatisfy(s -> assertThat(s).isNotBlank());
		assertThat(api.post("/api/quality/recalls", Map.of("kind", "recall", "source_type", "run", "source_id",
			999999, "reason", "x")).status()).isEqualTo(404);
		assertThat(api.post("/api/quality/recalls", Map.of("kind", "x", "source_type", "y")).json().get("errors")
			.has("reason")).isTrue();
	}

	@Test
	void documentsTrackReviewDatesAndTheReadinessPanelSummarises() {
		Map<String, Object> body = new HashMap<>(Map.of("code", "TEST-P-" + System.nanoTime() % 100000, "title",
			"Alerjen yönetimi prosedürü", "kind", "procedure", "clause", "BRCGS 5.3", "review_months", 12,
			"last_reviewed_at", LocalDate.now().minusMonths(13).toString()));
		JsonNode created = data(api.post("/api/quality/documents", body));
		assertThat(created.get("review_state").asString()).isEqualTo("overdue");
		long id = created.get("id").asLong();
		assertThat(api.post("/api/quality/documents", body).json().get("errors").has("code")).isTrue();

		JsonNode reviewed = data(api.post("/api/quality/documents/" + id + "/review", Map.of("version", "Rev 02")));
		assertThat(reviewed.get("review_state").asString()).isEqualTo("ok");
		assertThat(reviewed.get("version").asString()).isEqualTo("Rev 02");
		assertThat(reviewed.get("next_review_at").asString()).isEqualTo(LocalDate.now().plusMonths(12).toString());
		assertThat(data(api.patch("/api/quality/documents/" + id, Map.of("status", "obsolete"))).get("review_state")
			.isNull()).isTrue();
		assertThat(data(api.get("/api/quality/documents?review_state=overdue"))).allSatisfy(d -> assertThat(
			d.get("review_state").asString()).isEqualTo("overdue"));
		assertThat(api.delete("/api/quality/documents/" + id).status()).isEqualTo(204);

		JsonNode panel = data(api.get("/api/quality/readiness"));
		List<String> keys = new ArrayList<>();
		panel.get("items").forEach(i -> keys.add(i.get("key").asString()));
		assertThat(keys).containsExactly("documents", "drill", "recalls", "laboratory", "filling", "mass_balance");
		assertThat(panel.get("documents").get("active").asInt()).isGreaterThanOrEqualTo(15);
		assertThat(panel.get("laboratory").get("batches").asInt()).isPositive();
		assertThat(panel.get("mass_balance")).isNotEmpty();
	}

	@Test
	void permissions() {
		Api engineer = api.as("muhendis@olivamore.com");
		Api operator = api.as("operator1@olivamore.com");
		Api warehouse = api.as("depo@olivamore.com");

		assertThat(engineer.get("/api/quality/readiness").status()).isEqualTo(200);
		assertThat(operator.get("/api/quality/readiness").status()).isEqualTo(403);
		assertThat(warehouse.get("/api/quality/documents").status()).isEqualTo(403);
		assertThat(warehouse.post("/api/quality/recalls", Map.of()).status()).isEqualTo(403);
		long run = jdbc.queryForObject("select id from finished_product_runs where status = 'completed' limit 1",
			Long.class);
		assertThat(warehouse.get("/api/finished-product-runs/" + run + "/filling-checks").status()).isEqualTo(200);
		assertThat(api.as("uretim@olivamore.com").get("/api/finished-product-runs/" + run + "/filling-checks").status())
			.isEqualTo(403);
	}

	private JsonNode grade(Map<String, Object> values) {
		return data(api.post("/api/olive-oil-grade", Map.of("values", values)));
	}

	private String oilClass(long batch) {
		return jdbc.queryForObject("""
			select v.value_text from material_attribute_values v
			join materials m on m.id = v.material_id
			join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
			join attribute_definitions a on a.id = d.attribute_definition_id
			where m.production_batch_id = ? and a.attribute_key = 'oil_class'
			""", String.class, batch);
	}

}
