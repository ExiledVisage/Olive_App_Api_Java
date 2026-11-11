package com.olivamore.api.support;

import com.olivamore.api.support.ApiClient.Response;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo hattin (hat 1, girdi tipi 1 Gemlik zeytini, tedarikci 1) asamalarini API'den yuruten yardimci: 1 kantar tartimi
 * (zorunlu alan: plaka), 2 ayiklama, 3 ikinci tartim, 4 veri istemeyen asama, 5 sikim (donusum), 6 yag tartimi,
 * 7 pirina tartimi, 8 analiz, 9 on dinlendirme (onayli, dinlenmede bekler).
 */
public final class BatchFlow {

	public static final int PLATE_FIELD = 1;
	public static final int SORTING_FIELD = 3;
	/** 6. asamada tartilan yag (LITRE). */
	public static final String OIL = "899.75";

	private final Api api;
	private final JdbcTemplate jdbc;

	public BatchFlow(Api api, JdbcTemplate jdbc) {
		this.api = api;
		this.jdbc = jdbc;
	}

	public long batch(String code) {
		return id(api.post("/api/production-batches", Map.of("batch_code", code,
			"input_production_product_type_ids", List.of(1), "production_line_id", 1, "supplier_id", 1)));
	}

	public String process(long batch, int stage) {
		return "/api/batch-processes/" + jdbc.queryForObject("select id from batch_processes"
			+ " where production_batch_id = ? and production_stage_id = ?", Long.class, batch, stage);
	}

	public void fields(long batch, int stage, int field, String value) {
		Response r = api.post(process(batch, stage) + "/sync-field-values",
			Map.of("fields", List.of(Map.of("process_type_field_id", field, "value", value))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	public Response complete(long batch, int stage, Map<String, Object> operationData) {
		return api.patch(process(batch, stage), Map.of("status", "completed", "operation_data", operationData));
	}

	/** Bu asamaya kadar (dahil) butun asamalari gecerli verilerle tamamlar. */
	public void completeUpTo(long batch, int lastStage) {
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
				case 6 -> complete(batch, 6, Map.of("quantity", OIL));
				case 7 -> complete(batch, 7, Map.of("quantity", 3400));
				case 8 -> complete(batch, 8, Map.of("attributes", List.of(Map.of("attribute_key", "acidity",
					"value", "0.31"))));
				default -> throw new IllegalArgumentException("asama " + stage);
			};
			assertThat(r.status()).as("asama " + stage + ": " + r.body()).isEqualTo(200);
		}
	}

	/** On dinlendirme asamasi: tanka giris onaya gonderilir ve onaylanir, asama dinlenmede kalir. */
	public void rest(long batch, long tank, String quantity) {
		Response sent = complete(batch, 9, Map.of("quantity", quantity, "storage_unit_id", tank));
		assertThat(sent.status()).as(sent.body()).isEqualTo(200);
		Response approved = api.post(process(batch, 9) + "/approve", Map.of());
		assertThat(approved.status()).as(approved.body()).isEqualTo(200);
		assertThat(approved.json().get("data").get("status").asString()).isEqualTo("resting");
	}

	public static Map<String, Object> pressing() {
		return Map.of("input_quantity", 4800, "outputs", List.of(
			Map.of("product_type_key", "olive_oil", "quantity", "900.25"),
			Map.of("production_product_id", 3, "quantity", 3500, "unit", "KG")));
	}

}
