package com.olivamore.api.inventory;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Son tankta soy grafi ve materyal gecmisi (C7): tank materyali hic sifirlanmayabildiginden kenarlari zamanla
 * sinirlanir; partinin tanka girisi kendi gecmisinde gorulur; kok suzgeci tank materyallerini getirmez. Demo veri:
 * tip 3 Gemlik zeytinyagi (LITRE).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaterialLineageIT {

	private static final int GEMLIK = 3;

	private final AtomicInteger codes = new AtomicInteger();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StockOperations stock;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void batchMaterialLeadsOnlyToFillingsAfterItEnteredTheTank() {
		long tank = tank();
		long mix = materialOf(api.post("/api/storage-movements/opening", Map.of("storage_unit_id", tank,
			"production_product_type_id", GEMLIK, "quantity", 500, "unit", "LITRE")));
		List<Long> runs = runs(3);
		fill(tank, mix, runs.get(0), "40");
		long batch = batchMaterial("100");
		enter(tank, batch, "100");
		fill(tank, mix, runs.get(1), "40");
		// planlanmis, henuz cekilmemis kosu: giristen sonra sayilir
		reserve(mix, runs.get(2), "40");

		List<String> nodes = keys(data(api.get("/api/materials/" + batch + "/lineage")));

		assertThat(nodes).contains("material:" + mix, "run:" + runs.get(1), "run:" + runs.get(2))
			.doesNotContain("run:" + runs.get(0));
		// tank materyalinin kendi grafi sinirsiz
		assertThat(keys(data(api.get("/api/materials/" + mix + "/lineage"))))
			.contains("run:" + runs.get(0), "run:" + runs.get(1), "material:" + batch);
	}

	@Test
	void transferredTankMaterialLeadsBackOnlyToEntriesBeforeTheTransfer() {
		long from = tank();
		long to = tank();
		long before = batchMaterial("100");
		JsonNode first = data(enter(from, before, "100"));
		long mix = first.get("material_id").asLong();
		JsonNode moved = data(api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", from,
			"to_storage_unit_id", to, "quantity", 50)));
		long target = moved.get("in_movements").get(0).get("material_id").asLong();
		long after = batchMaterial("80");
		enter(from, after, "80");

		List<String> nodes = keys(data(api.get("/api/materials/" + target + "/lineage")));

		assertThat(nodes).contains("material:" + mix, "material:" + before).doesNotContain("material:" + after);
		assertThat(keys(data(api.get("/api/materials/" + mix + "/lineage"))))
			.contains("material:" + before, "material:" + after, "material:" + target);
	}

	@Test
	void historyOfABatchMaterialShowsItsTankEntry() {
		long tank = tank();
		long batch = batchMaterial("100");
		long mix = data(enter(tank, batch, "60")).get("material_id").asLong();

		JsonNode movements = data(api.get("/api/materials/" + batch)).get("movements");

		assertThat(movements).hasSize(1);
		assertThat(movements.get(0).get("movement_type").asString()).isEqualTo("IN");
		assertThat(movements.get(0).get("tank_material_id").asLong()).isEqualTo(mix);
		assertThat(movements.get(0).get("quantity").decimalValue()).isEqualByComparingTo("60");
		assertThat(data(api.get("/api/materials/" + mix)).get("movements").get(0).get("tank_material_id").isNull())
			.isTrue();
	}

	@Test
	void rootFilterLeavesTankMaterialsToTheirOwnFilter() {
		long tank = tank();
		long mix = materialOf(api.post("/api/storage-movements/opening", Map.of("storage_unit_id", tank,
			"production_product_type_id", GEMLIK, "quantity", 10, "unit", "LITRE")));

		assertThat(ids(data(api.get("/api/materials?lineage=root")))).doesNotContain(mix);
		assertThat(ids(data(api.get("/api/materials?lineage=tank")))).contains(mix);
		assertThat(data(api.get("/api/materials?lineage=tank")))
			.allMatch(m -> m.get("lineage_role").asString().equals("tank"));
	}

	// ---- yardimcilar -------------------------------------------------------

	private long tank() {
		return id(api.post("/api/storage-units", Map.of("name", "Son tank", "code", "LINEAGE-" + codes.incrementAndGet(),
			"capacity", 5000, "capacity_unit", "LITRE")));
	}

	/** Partide duran materyal (uretimin yazdigi gibi). */
	private long batchMaterial(String quantity) {
		long batch = jdbc.queryForObject("select id from production_batches order by id limit 1", Long.class);
		return jdbc.queryForObject("insert into materials (production_batch_id, production_product_type_id, "
			+ "origin_type, quantity, unit, status, current_location_type, current_location_id, created_at, updated_at) "
			+ "values (?, ?, 'BATCH_PROCESS', ?, 'LITRE', 'active', 'production_batch', ?, now(), now()) returning id",
			Long.class, batch, GEMLIK, new BigDecimal(quantity), batch);
	}

	private Response enter(long tank, long material, String quantity) {
		Response r = api.post("/api/storage-movements/inbound", Map.of("storage_unit_id", tank, "material_id", material,
			"quantity", new BigDecimal(quantity)));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
		return r;
	}

	/** Demo verideki kosular; dolum tanktan ceker (kosu tamamlaninca yapilan gibi). */
	private List<Long> runs(int count) {
		return jdbc.queryForList("select id from finished_product_runs order by id limit ?", Long.class, count);
	}

	private void fill(long tank, long mix, long run, String quantity) {
		reserve(mix, run, quantity);
		stock.outboundForRun(tank, mix, new BigDecimal(quantity), "Dolum", run);
	}

	private void reserve(long mix, long run, String quantity) {
		jdbc.update("insert into finished_product_run_materials (finished_product_run_id, source_material_id, "
			+ "quantity_used, created_at, updated_at) values (?, ?, ?, now(), now())", run, mix, new BigDecimal(quantity));
	}

	private static long materialOf(Response movement) {
		assertThat(movement.status()).as(movement.body()).isEqualTo(201);
		return data(movement).get("material_id").asLong();
	}

	private static List<String> keys(JsonNode lineage) {
		List<String> keys = new ArrayList<>();
		lineage.get("nodes").forEach(n -> keys.add(n.get("key").asString()));
		return keys;
	}

	private static List<Long> ids(JsonNode materials) {
		List<Long> ids = new ArrayList<>();
		materials.forEach(m -> ids.add(m.get("id").asLong()));
		return ids;
	}

}
