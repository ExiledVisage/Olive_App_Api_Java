package com.olivamore.api.finishedproduct;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.OilInLitres;
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
 * Sisedeki QR (docs/GREENOPS_FEATURES.md 5b): baslamis/bitmis kosuya herkese acik bag; sayfa girissiz acilir,
 * urun, lot, asitlik ve tanka dolumdan once giren partilerin tedarikcisini gosterir (sonra girenler yok); bag
 * kaldirilinca 404. Demo veri: urun 1 (Gemlik 0.3), tip 3 Gemlik, esleme 4 asitlik.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicTraceIT {

	private static final int GEMLIK = 3;
	private static final int ACIDITY = 4;
	private static final int GEMLIK_03 = 1;

	private final AtomicInteger codes = new AtomicInteger();

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

	@Test
	void pageShowsTheRunAndTheSuppliersThatFedTheTankBeforeFilling() {
		List<Map<String, Object>> deliveries = jdbc.queryForList("""
			select b.id, s.name from production_batches b join suppliers s on s.id = b.supplier_id
			where b.parent_batch_id is null and b.received_at is not null
			order by (b.supplier_field_id is null) desc, b.id limit 50
			""");
		// tarlasi girilmemis teslimat: sayfada yalnizca tedarikci
		Map<String, Object> before = deliveries.getFirst();
		Map<String, Object> after = deliveries.stream().filter(d -> !d.get("name").equals(before.get("name")))
			.findFirst().orElseThrow();
		long tank = tank();
		enter(tank, batchMaterial(((Number) before.get("id")).longValue(), "100"));
		long run = id(api.post("/api/finished-product-runs", Map.of("finished_product_id", GEMLIK_03, "quantity", 10,
			"source", Map.of("storage_unit_id", tank))));
		status(run, "in_progress");
		status(run, "completed");
		enter(tank, batchMaterial(((Number) after.get("id")).longValue(), "100"));

		Response link = api.post("/api/finished-product-runs/" + run + "/public-link", Map.of());
		String url = data(link).get("url").asString();
		String token = url.substring(url.lastIndexOf('/') + 1);
		Response page = api.as(null).get("/api/public/runs/" + token);

		assertThat(link.status()).as(link.body()).isEqualTo(200);
		assertThat(url).contains("/api/public/runs/");
		assertThat(page.status()).as(page.body()).isEqualTo(200);
		assertThat(page.body()).contains("deneme", "Dolum #" + run, "0.3", (String) before.get("name"))
			.doesNotContain((String) after.get("name"));
		assertThat(data(api.get("/api/finished-product-runs/" + run)).get("public_url").asString()).isEqualTo(url);
		// ayni bag tekrar istenince degismez, yenilenince degisir
		assertThat(data(api.post("/api/finished-product-runs/" + run + "/public-link", Map.of())).get("url")
			.asString()).isEqualTo(url);
		assertThat(data(api.post("/api/finished-product-runs/" + run + "/public-link?regenerate=true", Map.of()))
			.get("url").asString()).isNotEqualTo(url);
		assertThat(api.as(null).get("/api/public/runs/" + token).status()).isEqualTo(404);
	}

	@Test
	void plannedRunHasNoLinkAndRevokedLinkIsGone() {
		long tank = tank();
		long batch = jdbc.queryForObject("select id from production_batches where parent_batch_id is null order by id"
			+ " limit 1", Long.class);
		enter(tank, batchMaterial(batch, "100"));
		long run = id(api.post("/api/finished-product-runs", Map.of("finished_product_id", GEMLIK_03, "quantity", 4,
			"source", Map.of("storage_unit_id", tank))));

		Response planned = api.post("/api/finished-product-runs/" + run + "/public-link", Map.of());
		status(run, "in_progress");
		String url = data(api.post("/api/finished-product-runs/" + run + "/public-link", Map.of())).get("url")
			.asString();
		Response revoked = api.delete("/api/finished-product-runs/" + run + "/public-link");

		assertThat(planned.status()).isEqualTo(422);
		assertThat(revoked.status()).isEqualTo(204);
		assertThat(api.as(null).get(url.substring(url.indexOf("/api/"))).status()).isEqualTo(404);
		assertThat(api.as(null).get("/api/public/runs/yok").body()).contains("kayıt yok");
	}

	private long tank() {
		return id(api.post("/api/storage-units", Map.of("name", "QR", "code", "TRACE-" + codes.incrementAndGet(),
			"capacity", 5000, "capacity_unit", "LITRE")));
	}

	/** Partide duran Gemlik yagi (uretimin yazdigi gibi), asitlik 0.3. */
	private long batchMaterial(long batch, String litres) {
		long id = jdbc.queryForObject("insert into materials (production_batch_id, production_product_type_id, "
			+ "origin_type, quantity, unit, status, current_location_type, current_location_id, created_at, updated_at) "
			+ "values (?, ?, 'BATCH_PROCESS', ?, 'LITRE', 'active', 'production_batch', ?, now(), now()) returning id",
			Long.class, batch, GEMLIK, new BigDecimal(litres), batch);
		jdbc.update("insert into material_attribute_values (material_id, production_product_attribute_definition_id, "
			+ "value_decimal, created_at, updated_at) values (?, ?, 0.3, now(), now())", id, ACIDITY);
		return id;
	}

	private void enter(long tank, long material) {
		Response r = api.post("/api/storage-movements/inbound", Map.of("storage_unit_id", tank, "material_id",
			material, "quantity", 100));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
	}

	private void status(long run, String status) {
		Response r = api.patch("/api/finished-product-runs/" + run + "/status", Map.of("status", status));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

}
