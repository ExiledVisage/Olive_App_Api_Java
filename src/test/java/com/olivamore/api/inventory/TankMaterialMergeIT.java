package com.olivamore.api.inventory;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * V10'un eski veriyi birlestirme kurali, calisma anindaki son tank girisiyle ({@code TankMixAttributes}) ayni sonucu
 * verir: ayni materyaller bir tanka API'den tek tek girer, digerine eski haliyle (tankta duran parti materyalleri)
 * konup V10 calistirilir. Demo veri V10'dan gecmis haldedir (seed = Laravel verisi + migration'lar). Demo veri:
 * tip 3 Gemlik zeytinyagi (LITRE, kategori yogunlugu 0.916); esleme 4 asitlik (decimal), 5 cesit (string).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TankMaterialMergeIT {

	private static final int GEMLIK = 3;
	private static final int ACIDITY = 4;
	private static final int CULTIVAR = 5;
	private static final String V10 = "db/migration/V10__merge_final_tank_materials.sql";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void mergingOldMaterialsGivesTheSameTankMaterialAsEnteringThemOneByOne() throws IOException {
		long entered = tank("PARITY-API");
		long merged = tank("PARITY-V10");
		List<Map<String, String>> materials = List.of(
			Map.of("quantity", "100", "unit", "LITRE", "acidity", "0.30", "cultivar", "Gemlik"),
			Map.of("quantity", "91.6", "unit", "KG", "acidity", "0.50", "cultivar", "Gemlik"),
			Map.of("quantity", "50", "unit", "LITRE", "acidity", "0.20", "cultivar", "Gemlik"));
		for (Map<String, String> m : materials) {
			long material = material(m, "production_batch", batch());
			Response in = api.post("/api/storage-movements/inbound", Map.of("storage_unit_id", entered,
				"material_id", material, "quantity", new BigDecimal(m.get("quantity"))));
			assertThat(in.status()).as(in.body()).isEqualTo(201);
			material(m, "storage_unit", merged);
		}

		migrate();

		long viaApi = tankMaterial(entered);
		long viaMigration = tankMaterial(merged);
		String row = "select quantity || ' ' || unit || ' ' || origin_type from materials where id = ?";
		// 91.6 kg / 0.916 = 100 L; (100 x 0.30 + 100 x 0.50 + 50 x 0.20) / 250 = 0.36
		assertThat(jdbc.queryForObject(row, String.class, viaMigration))
			.isEqualTo(jdbc.queryForObject(row, String.class, viaApi)).isEqualTo("250.0000 LITRE TANK");
		assertThat(values(viaMigration)).isEqualTo(values(viaApi))
			.containsExactly(ACIDITY + " 0.3600 -", CULTIVAR + " - Gemlik");
		assertThat(balances(merged)).isEqualTo(balances(entered)).containsExactly("250.00 LITRE");
	}

	@Test
	void demoDataHoldsOneTankMaterialPerTypeAndAnotherRunChangesNothing() throws IOException {
		String counts = """
			select (select count(*) from materials m join storage_units u on u.id = m.current_location_id
					where m.current_location_type = 'storage_unit' and m.status = 'active' and u.kind = 'final'
						and m.origin_type <> 'TANK')
				|| ' ' || (select count(*) from materials where origin_type = 'TANK')
				|| ' ' || (select count(*) from storage_movements)
			""";
		String before = jdbc.queryForObject(counts, String.class);

		migrate();

		assertThat(before).startsWith("0 ").isEqualTo(jdbc.queryForObject(counts, String.class));
		assertThat(jdbc.queryForObject("""
			select count(*) from (select 1 from materials
				where current_location_type = 'storage_unit' and status = 'active' and origin_type = 'TANK'
				group by current_location_id, production_product_type_id having count(*) > 1) duplicate
			""", Long.class)).isZero();
		assertThat(jdbc.queryForObject("""
			select count(*) from storage_unit_balances b join storage_units u on u.id = b.storage_unit_id
			where u.kind = 'final' and b.current_quantity <> (select coalesce(round(sum(m.quantity), 2), 0) from materials m
				join production_product_types t on t.id = m.production_product_type_id
				where m.current_location_type = 'storage_unit' and m.current_location_id = b.storage_unit_id
					and m.status = 'active' and m.unit = b.unit and t.production_product_id = b.production_product_id)
			""", Long.class)).isZero();
	}

	// ---- yardimcilar -------------------------------------------------------

	/** V10 bir islemde (gecici tablolari commit'te silinir); onceki kayitlar zaten birlesmisse dokunmaz. */
	private void migrate() throws IOException {
		String script = new ClassPathResource(V10).getContentAsString(StandardCharsets.UTF_8);
		new TransactionTemplate(transactions).executeWithoutResult(status -> jdbc.execute(script));
	}

	private long tank(String code) {
		return id(api.post("/api/storage-units", Map.of("name", "Son tank", "code", code, "capacity", 5000,
			"capacity_unit", "LITRE")));
	}

	private long batch() {
		return jdbc.queryForObject("select id from production_batches order by id limit 1", Long.class);
	}

	/** Partide ya da (gecisten onceki gibi) dogrudan tankta duran materyal. */
	private long material(Map<String, String> m, String locationType, long locationId) {
		long id = jdbc.queryForObject("insert into materials (production_batch_id, production_product_type_id, "
			+ "origin_type, quantity, unit, status, current_location_type, current_location_id, created_at, updated_at) "
			+ "values (?, ?, 'BATCH_PROCESS', ?, ?, 'active', ?, ?, now(), now()) returning id", Long.class, batch(),
			GEMLIK, new BigDecimal(m.get("quantity")), m.get("unit"), locationType, locationId);
		jdbc.update("insert into material_attribute_values (material_id, production_product_attribute_definition_id, "
			+ "value_decimal, created_at, updated_at) values (?, ?, ?, now(), now())", id, ACIDITY,
			new BigDecimal(m.get("acidity")));
		jdbc.update("insert into material_attribute_values (material_id, production_product_attribute_definition_id, "
			+ "value_text, created_at, updated_at) values (?, ?, ?, now(), now())", id, CULTIVAR, m.get("cultivar"));
		return id;
	}

	private long tankMaterial(long tank) {
		return jdbc.queryForObject("select id from materials where origin_type = 'TANK' and status = 'active' "
			+ "and current_location_id = ?", Long.class, tank);
	}

	private List<String> values(long material) {
		return jdbc.queryForList("select production_product_attribute_definition_id || ' ' || coalesce(value_decimal::text,"
			+ " '-') || ' ' || coalesce(value_text, '-') from material_attribute_values where material_id = ?"
			+ " order by production_product_attribute_definition_id", String.class, material);
	}

	private List<String> balances(long tank) {
		return jdbc.queryForList("select current_quantity || ' ' || unit from storage_unit_balances"
			+ " where storage_unit_id = ? order by unit", String.class, tank);
	}

}
