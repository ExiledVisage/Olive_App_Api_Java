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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uretim asamalarinin kurallari, her test kendi partisiyle; hattin asamalari {@link BatchFlow}'da. On dinlendirme
 * tankinin akisi (dinlenme, iptal, bosaltma, tortu) {@code PreSettlingRulesIT}'te.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchStageRulesIT {

	private static final int PLATE_FIELD = BatchFlow.PLATE_FIELD;
	private static final int RESTING_TANK = 7;

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
		Response balanced = complete(batch, 5, BatchFlow.pressing());

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
			.isEqualTo("resting");
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
		return flow.batch("T-RULE-" + codes.incrementAndGet());
	}

	private String process(long batch, int stage) {
		return flow.process(batch, stage);
	}

	private void fields(long batch, int stage, int field, String value) {
		flow.fields(batch, stage, field, value);
	}

	private Response complete(long batch, int stage, Map<String, Object> operationData) {
		return flow.complete(batch, stage, operationData);
	}

	private void completeUpTo(long batch, int lastStage) {
		flow.completeUpTo(batch, lastStage);
	}

	private static Map<String, Object> resting() {
		return Map.of("quantity", BatchFlow.OIL, "storage_unit_id", RESTING_TANK);
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
