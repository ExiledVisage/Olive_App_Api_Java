package com.olivamore.api.support;

import com.olivamore.api.support.ApiClient.Response;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo hattin (hat 1, girdi tipi 1 Gemlik zeytini, tedarikci 1) asamalarini API'den yuruten yardimci; parti
 * {@link #batch} ile acilan grup. Asama id'leri: 1 kantar tartimi (plaka partide), 2 ayiklama (veri yok),
 * 3 ikinci tartim, 4 veri istemeyen asama, 5 sikim (donusum; yag ve kalan cikti pirina), 8 analiz, 9 on dinlendirme
 * (onayli, dinlenmede bekler), 10 filtreleme, 12 randiman.
 */
public final class BatchFlow {

	/** Sikimda cikan yag. */
	public static final String OIL = "900.25";

	/** {@link #completeUpTo} sirasi: hattin asama id'leri. */
	private static final List<Integer> STAGES = List.of(1, 2, 3, 4, 5, 8);

	private final Api api;
	private final JdbcTemplate jdbc;

	public BatchFlow(Api api, JdbcTemplate jdbc) {
		this.api = api;
		this.jdbc = jdbc;
	}

	/** Tartilmamis teslimat (kantar bekliyor). */
	public long delivery(String code) {
		return id(api.post("/api/production-batches", Map.of("batch_code", code,
			"input_production_product_type_ids", List.of(1), "production_line_id", 1, "supplier_id", 1,
			"truck_plate", "35 ABC 123")));
	}

	/** Kantarda {@code kg} ile tartilmis teslimat. */
	public long weighedDelivery(String code, Object kg) {
		return id(api.post("/api/production-batches", Map.of("batch_code", code,
			"input_production_product_type_ids", List.of(1), "production_line_id", 1, "supplier_id", 1,
			"truck_plate", "35 ABC 123", "quantity_kg", kg)));
	}

	/**
	 * Butun asamalari kendisi yapacak grup: 5000 kg'lik teslimatin hepsi, Kantarda Tartim'dan baslar (teslimatin
	 * kantari gecilmez). Asama kurallari bu grupta denenir.
	 */
	public long batch(String code) {
		long delivery = weighedDelivery(code, 5000);
		return id(api.post("/api/production-batches/" + delivery + "/groups",
			Map.of("all_remaining", true, "start_stage_id", 1)));
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

	/** Kronometreli asamada (5 sikim, 10 filtreleme) makineyi baslatip bitirir. */
	public void runMachine(long batch, int stage) {
		for (String action : List.of("start", "finish")) {
			Response r = api.post(process(batch, stage) + "/machine/" + action, Map.of());
			assertThat(r.status()).as(action + ": " + r.body()).isEqualTo(200);
		}
	}

	/** Bu asama id'sine kadar (dahil) hattaki butun asamalari gecerli verilerle tamamlar. */
	public void completeUpTo(long batch, int lastStage) {
		for (int stage : STAGES.stream().filter(s -> s <= lastStage).toList()) {
			Response r = switch (stage) {
				case 1 -> complete(batch, 1, Map.of("quantity", 5000, "unit", "KG"));
				case 2 -> complete(batch, 2, Map.of("x", 1));
				case 3 -> complete(batch, 3, Map.of("quantity", 4800));
				case 4 -> api.patch(process(batch, 4), Map.of("status", "completed"));
				case 5 -> {
					runMachine(batch, 5);
					yield complete(batch, 5, pressing());
				}
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

	/** Pirina kalan ciktidir: hesaplanan 4800 - 900.25 = 3899.75, girilen 3500 (sapma -0.1025). */
	/** Tanktaki gruplarin filtrelemesini (asama 10) baslatir; Boşalt'tan once gerekir. */
	public void startFiltering(long tank) {
		Response r = api.post("/api/storage-units/" + tank + "/filtering/start", Map.of());
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	public static Map<String, Object> pressing() {
		return Map.of("input_quantity", 4800, "outputs", List.of(
			Map.of("product_type_key", "olive_oil", "quantity", OIL),
			Map.of("production_product_id", 3, "quantity", 3500, "unit", "KG")));
	}

}
