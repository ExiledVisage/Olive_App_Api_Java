package com.olivamore.api.production;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uretim asamalarinin kurallari, her test kendi partisiyle (hat 1, girdi tipi 1, tedarikci 1). Hattin asamalari:
 * 1 kantar tartimi (zorunlu alan: plaka), 2 ayiklama, 3 ikinci tartim, 4 veri istemeyen asama, 5 sikim (donusum),
 * 6 yag tartimi, 7 pirina tartimi, 8 analiz, 9 on dinlenme tankina giris (onayli).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchStageRulesIT {

	private static final int PLATE_FIELD = 1;
	private static final int SORTING_FIELD = 3;
	private static final int RESTING_TANK = 7;

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
	void stagesFollowTheOrderOfTheLine() {
		long batch = batch();

		Response r = api.patch(process(batch, 2), Map.of("status", "in_progress"));

		assertRule(r, "'Ayıklama' aşamasına geçilemez; önceki aşamalar tamamlanmadı: Kantarda Tartım.");
	}

	@Test
	void requiredFieldsMustBeFilledBeforeCompletion() {
		long batch = batch();

		Response r = complete(batch, 1, Map.of("quantity", 5000));

		assertRule(r, "Zorunlu alanlar doldurulmadan aşama tamamlanamaz: Kamyon Plakası.");
	}

	@Test
	void weighingCreatesTheBatchMaterialInTheProductUnit() {
		long batch = batch();
		fields(batch, 1, PLATE_FIELD, "16 ABC 123");

		assertRule(complete(batch, 1, Map.of("quantity", 5000, "unit", "LITRE")),
			"Tartım birimi ürün birimi ile uyuşmuyor.");
		assertThat(complete(batch, 1, Map.of("quantity", "5000.5", "unit", "KG")).status()).isEqualTo(200);

		assertThat(activeMaterials(batch)).containsExactly(new BigDecimal("5000.5000"));
	}

	@Test
	void aLaterWeighingCanOnlyReduceTheQuantity() {
		long batch = batch();
		completeUpTo(batch, 2);

		Response more = complete(batch, 3, Map.of("quantity", 6000));
		Response less = complete(batch, 3, Map.of("quantity", 4800));

		assertThat(more.status()).isEqualTo(422);
		assertThat(more.json().get("detail").asString()).startsWith("Tartım kayıtlı miktarı artıramaz");
		assertThat(less.status()).as(less.body()).isEqualTo(200);
		assertThat(activeMaterials(batch)).containsExactly(new BigDecimal("4800.0000"));
	}

	@Test
	void completedStageThatCreatedRecordsCannotBeReopened() {
		long batch = batch();
		completeUpTo(batch, 1);

		Response r = api.patch(process(batch, 1), Map.of("status", "waiting"));

		assertThat(r.status()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).startsWith("Bu proses tamamlanırken 1 materyal oluşturdu");
	}

	@Test
	void transformationOutputsInTheInputUnitCannotExceedTheInput() {
		long batch = batch();
		completeUpTo(batch, 4);

		Response unbalanced = complete(batch, 5, Map.of("input_quantity", 4800, "outputs", List.of(
			Map.of("product_type_key", "olive_oil", "quantity", 900),
			Map.of("production_product_id", 3, "quantity", 5000))));
		Response balanced = complete(batch, 5, pressing());

		assertRule(unbalanced, "Kütle dengesi tutmuyor: girdi 4800 KG, aynı birimdeki çıktı toplamı 5000 KG. "
			+ "Çıktı girdiden fazla olamaz.");
		assertThat(balanced.status()).as(balanced.body()).isEqualTo(200);
		// girdi tukendi; cikti yag (litre) ve pirina (kg)
		assertThat(activeMaterials(batch)).containsExactlyInAnyOrder(new BigDecimal("900.2500"),
			new BigDecimal("3500.0000"));
	}

	@Test
	void analysisWritesTheAttributeOnTheMaterial() {
		long batch = batch();
		completeUpTo(batch, 7);

		Response r = complete(batch, 8, Map.of("attributes", List.of(Map.of("attribute_key", "acidity", "value", "0.31"))));

		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(jdbc.queryForList("""
			select v.value_decimal from material_attribute_values v
			join materials m on m.id = v.material_id
			join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
			join attribute_definitions a on a.id = d.attribute_definition_id
			where m.production_batch_id = ? and a.attribute_key = 'acidity'
			""", BigDecimal.class, batch)).containsExactly(new BigDecimal("0.3100"));
	}

	@Test
	void storageStageWaitsForApprovalAndCanBeSentBack() {
		long batch = batch();
		completeUpTo(batch, 8);
		String stage = process(batch, 9);

		assertThat(data(complete(batch, 9, resting())).get("status").asString()).isEqualTo("awaiting_approval");
		Response withoutNote = api.post(stage + "/reject", Map.of());
		assertThat(withoutNote.status()).isEqualTo(422);
		assertThat(withoutNote.json().get("errors").get("note").get(0).asString()).isEqualTo("The note field is required.");
		assertThat(data(api.post(stage + "/reject", Map.of("note", "Miktar yanlış"))).get("status").asString())
			.isEqualTo("in_progress");

		assertThat(data(complete(batch, 9, resting())).get("status").asString()).isEqualTo("awaiting_approval");
		assertThat(data(api.post(stage + "/approve", Map.of("note", "Tamam"))).get("status").asString())
			.isEqualTo("completed");
		assertThat(jdbc.queryForObject("select count(*) from materials where production_batch_id = ? and status = 'active'"
			+ " and current_location_type = 'storage_unit' and current_location_id = ?", Long.class, batch, RESTING_TANK))
			.isEqualTo(1);
	}

	@Test
	void plannerCannotCompleteAStage() {
		long batch = batch();
		fields(batch, 1, PLATE_FIELD, "16 ABC 123");

		Response r = api.as("planner@olivamore.com").patch(process(batch, 1),
			Map.of("status", "completed", "operation_data", Map.of("quantity", 5000)));

		assertThat(r.status()).isEqualTo(403);
	}

	// ---- yardimcilar -------------------------------------------------------

	private long batch() {
		return id(api.post("/api/production-batches", Map.of("batch_code", "T-RULE-" + codes.incrementAndGet(),
			"input_production_product_type_ids", List.of(1), "production_line_id", 1, "supplier_id", 1)));
	}

	private String process(long batch, int stage) {
		return "/api/batch-processes/" + jdbc.queryForObject("select id from batch_processes"
			+ " where production_batch_id = ? and production_stage_id = ?", Long.class, batch, stage);
	}

	private void fields(long batch, int stage, int field, String value) {
		Response r = api.post(process(batch, stage) + "/sync-field-values",
			Map.of("fields", List.of(Map.of("process_type_field_id", field, "value", value))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	private Response complete(long batch, int stage, Map<String, Object> operationData) {
		return api.patch(process(batch, stage), Map.of("status", "completed", "operation_data", operationData));
	}

	/** Bu asamaya kadar (dahil) butun asamalari gecerli verilerle tamamlar. */
	private void completeUpTo(long batch, int lastStage) {
		for (int stage = 1; stage <= lastStage; stage++) {
			Response r = switch (stage) {
				case 1 -> {
					fields(batch, 1, PLATE_FIELD, "16 ABC 123");
					yield complete(batch, 1, Map.of("quantity", 5000, "unit", "KG"));
				}
				case 2 -> {
					fields(batch, 2, SORTING_FIELD, "2.5");
					yield complete(batch, 2, Map.of("x", 1));
				}
				case 3 -> complete(batch, 3, Map.of("quantity", 4800));
				case 4 -> api.patch(process(batch, 4), Map.of("status", "completed"));
				case 5 -> complete(batch, 5, pressing());
				case 6 -> complete(batch, 6, Map.of("quantity", "899.75"));
				case 7 -> complete(batch, 7, Map.of("quantity", 3400));
				case 8 -> complete(batch, 8, Map.of("attributes", List.of(Map.of("attribute_key", "acidity",
					"value", "0.31"))));
				default -> throw new IllegalArgumentException("asama " + stage);
			};
			assertThat(r.status()).as("asama " + stage + ": " + r.body()).isEqualTo(200);
		}
	}

	private static Map<String, Object> pressing() {
		return Map.of("input_quantity", 4800, "outputs", List.of(
			Map.of("product_type_key", "olive_oil", "quantity", "900.25"),
			Map.of("production_product_id", 3, "quantity", 3500, "unit", "KG")));
	}

	private static Map<String, Object> resting() {
		return Map.of("quantity", "899.75", "storage_unit_id", RESTING_TANK, "input_quantity", 20,
			"outputs", List.of(Map.of("production_product_id", 3, "quantity", "15.5")));
	}

	private List<BigDecimal> activeMaterials(long batch) {
		return jdbc.queryForList("select quantity from materials where production_batch_id = ? and status = 'active'"
			+ " order by id", BigDecimal.class, batch);
	}

	private static void assertRule(Response r, String detail) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).isEqualTo(detail);
	}

}
