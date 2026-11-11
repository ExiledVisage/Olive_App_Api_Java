package com.olivamore.api.inventory;

import com.olivamore.api.shared.error.BusinessRuleException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Son tank (C2): tank ve urun tipi basina tek tank materyali, giriste gelenin kapanmasi, kutleye gore agirlikli
 * ortalama, zorunlu attribute sarti, karisimdan oransal cikis, tamami-ya-da-hic transfer, uretim icinden karisima
 * dokunulamamasi, tank analizi ve sapma gecmisi, soy grafi. Demo veri: tip 3 Gemlik ve tip 4 Ayvalik zeytinyagi
 * (urun 2, LITRE, kategori yogunlugu 0.916); esleme 4 asitlik (decimal), 5 cesit (string).
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinalTankRulesIT {

	private static final int GEMLIK = 3;
	private static final int AYVALIK = 4;
	private static final int OLIVE_OIL = 2;
	private static final int ACIDITY = 4;
	private static final int CULTIVAR = 5;

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
	void entriesOfOneTypeMergeIntoOneTankMaterialAndTheEnteredMaterialCloses() {
		long tank = tank(1000, Map.of());

		JsonNode first = data(open(tank, GEMLIK, "100", "0.3"));
		JsonNode second = data(open(tank, GEMLIK, "50", "0.6"));

		long mix = first.get("material_id").asLong();
		assertThat(second.get("material_id").asLong()).isEqualTo(mix);
		assertThat(active(tank)).containsExactly(mix);
		assertThat(quantity(mix)).isEqualByComparingTo("150");
		assertThat(jdbc.queryForObject("select origin_type from materials where id = ?", String.class, mix))
			.isEqualTo("TANK");
		assertThat(jdbc.queryForObject("select production_batch_id from materials where id = ?", Long.class, mix))
			.isNull();
		long entered = second.get("source_material_id").asLong();
		assertThat(status(entered)).isEqualTo("consumed");
		assertThat(quantity(entered)).isEqualByComparingTo("50");
		// (100 x 0.3 + 50 x 0.6) / 150
		assertThat(acidity(mix)).isEqualByComparingTo("0.4");
	}

	@Test
	void blendTankKeepsOneTankMaterialPerType() {
		long tank = tank(5000, Map.of("allow_multiple_production_product_types", true));
		long third = litreType("0.92");

		long gemlik = materialOf(open(tank, GEMLIK, "100", "0.3"));
		long ayvalik = materialOf(open(tank, AYVALIK, "100", "0.5"));
		long other = materialOf(open(tank, third, "100", null));
		long again = materialOf(open(tank, GEMLIK, "100", "0.5"));

		assertThat(again).isEqualTo(gemlik);
		assertThat(active(tank)).containsExactly(gemlik, ayvalik, other);
		// tipler karismaz: Gemlik kendi icinde (100 x 0.3 + 100 x 0.5) / 200, Ayvalik degismez
		assertThat(acidity(gemlik)).isEqualByComparingTo("0.4");
		assertThat(acidity(ayvalik)).isEqualByComparingTo("0.5");
	}

	@Test
	void numericValuesUseTheWeightedAverageAndOtherValuesStayOnlyWhileEqual() {
		long tank = tank(5000, Map.of());

		long mix = materialOf(opening(tank, GEMLIK, "1000", "LITRE", attributes("0.30", "Gemlik")));
		assertThat(opening(tank, GEMLIK, "500", "LITRE", attributes("0.50", "Gemlik")).status()).isEqualTo(201);

		// (1000 x 0.30 + 500 x 0.50) / 1500 = 0.36667
		assertThat(acidity(mix)).isEqualByComparingTo("0.3667");
		assertThat(text(mix, CULTIVAR)).isEqualTo("Gemlik");

		assertThat(opening(tank, GEMLIK, "100", "LITRE", attributes("0.3667", "Edremit")).status()).isEqualTo(201);
		assertThat(text(mix, CULTIVAR)).isNull();

		// degeri olmayan giris karisimin degerini bilinmez yapar
		assertThat(opening(tank, GEMLIK, "100", "LITRE", null).status()).isEqualTo(201);
		assertThat(acidity(mix)).isNull();
	}

	@Test
	void entryInAnotherUnitIsConvertedToTheTypeUnit() {
		long tank = tank(1000, Map.of());
		long mix = materialOf(open(tank, GEMLIK, "100", "0.3"));
		// 91.6 kg / 0.916 = 100 L
		long kilograms = batchMaterial(GEMLIK, "91.6", "KG", "0.5");

		JsonNode in = data(api.post("/api/storage-movements/inbound",
			Map.of("storage_unit_id", tank, "material_id", kilograms, "quantity", "91.6")));

		assertThat(in.get("material_id").asLong()).isEqualTo(mix);
		assertThat(in.get("unit").asString()).isEqualTo("LITRE");
		assertThat(quantity(mix)).isEqualByComparingTo("200");
		assertThat(acidity(mix)).isEqualByComparingTo("0.4");
	}

	@Test
	void partialEntryKeepsTheRestActiveInProduction() {
		long tank = tank(1000, Map.of());
		long batch = batchMaterial(GEMLIK, "100", "LITRE", "0.3");

		JsonNode first = data(api.post("/api/storage-movements/inbound",
			Map.of("storage_unit_id", tank, "material_id", batch, "quantity", 40)));
		assertThat(first.get("source_material_id").asLong()).isEqualTo(batch);
		assertThat(quantity(batch)).isEqualByComparingTo("60");
		assertThat(status(batch)).isEqualTo("active");
		assertThat(jdbc.queryForObject("select current_location_type from materials where id = ?", String.class, batch))
			.isEqualTo("production_batch");

		assertThat(api.post("/api/storage-movements/inbound",
			Map.of("storage_unit_id", tank, "material_id", batch, "quantity", 60)).status()).isEqualTo(201);
		assertThat(status(batch)).isEqualTo("consumed");
		assertThat(quantity(first.get("material_id").asLong())).isEqualByComparingTo("100");
	}

	@Test
	void unmeasuredMaterialCannotEnterAFinalTank() {
		long last = tank(1000, Map.of());
		jdbc.update("update production_product_attribute_definitions set is_required = true where id = ?", ACIDITY);
		try {
			assertRule(opening(last, GEMLIK, "10", "LITRE", null),
				"Ölçülmemiş materyal son tanka giremez. Eksik zorunlu özellikler: Asitlik.");
			assertThat(open(last, GEMLIK, "10", "0.3").status()).isEqualTo(201);
		}
		finally {
			jdbc.update("update production_product_attribute_definitions set is_required = false where id = ?",
				ACIDITY);
		}
	}

	@Test
	void outboundTakesFromEveryTypeInProportion() {
		long tank = tank(5000, Map.of("allow_multiple_production_product_types", true));
		long third = litreType("0.916");
		long gemlik = materialOf(open(tank, GEMLIK, "600", "0.3"));
		long ayvalik = materialOf(open(tank, AYVALIK, "300", "0.4"));
		long other = materialOf(open(tank, third, "100", null));

		JsonNode out = data(api.post("/api/storage-movements/outbound", Map.of("storage_unit_id", tank, "quantity", 100)));

		assertThat(out).hasSize(3);
		assertThat(quantity(gemlik)).isEqualByComparingTo("540");
		assertThat(quantity(ayvalik)).isEqualByComparingTo("270");
		assertThat(quantity(other)).isEqualByComparingTo("90");
		// cikis ortalamayi degistirmez
		assertThat(acidity(gemlik)).isEqualByComparingTo("0.3");
	}

	@Test
	void roundingDifferenceOfAProportionalOutboundGoesToTheLastType() {
		long tank = tank(5000, Map.of("allow_multiple_production_product_types", true));
		long third = litreType("0.916");
		long gemlik = materialOf(open(tank, GEMLIK, "1", null));
		long ayvalik = materialOf(open(tank, AYVALIK, "1", null));
		long other = materialOf(open(tank, third, "1", null));

		// her tipe 1/3: 0.3333 ve 0.3333, kalan 0.3334 son tipe
		JsonNode out = data(api.post("/api/storage-movements/outbound", Map.of("storage_unit_id", tank, "quantity", 1)));

		assertThat(out).hasSize(3);
		assertThat(quantity(gemlik)).isEqualByComparingTo("0.6667");
		assertThat(quantity(ayvalik)).isEqualByComparingTo("0.6667");
		assertThat(quantity(other)).isEqualByComparingTo("0.6666");
	}

	@Test
	void zeroedTankMaterialClosesAndTheNextEntryOpensANewOne() {
		long tank = tank(1000, Map.of());
		long first = materialOf(open(tank, GEMLIK, "100", "0.3"));

		data(api.post("/api/storage-movements/outbound", Map.of("storage_unit_id", tank, "quantity", 100)));
		long second = materialOf(open(tank, GEMLIK, "50", "0.6"));

		assertThat(status(first)).isEqualTo("consumed");
		assertThat(second).isNotEqualTo(first);
		assertThat(active(tank)).containsExactly(second);
		assertThat(acidity(second)).isEqualByComparingTo("0.6");
	}

	@Test
	void transferNeedsTheTargetToAcceptEveryTypeOfTheMix() {
		long from = tank(5000, Map.of("allow_multiple_production_product_types", true));
		long gemlikOnly = tank(5000, Map.of());
		long blend = tank(5000, Map.of("allow_multiple_production_product_types", true));
		allowTypes(gemlikOnly, List.of(GEMLIK));
		long gemlik = materialOf(open(from, GEMLIK, "600", "0.3"));
		long ayvalik = materialOf(open(from, AYVALIK, "400", "0.5"));

		assertRule(api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", from,
			"to_storage_unit_id", gemlikOnly, "quantity", 100)), "Seçilen tank bu spesifik ürünü kabul etmiyor.");
		assertThat(quantity(gemlik)).isEqualByComparingTo("600");
		assertThat(quantity(ayvalik)).isEqualByComparingTo("400");
		assertThat(active(gemlikOnly)).isEmpty();

		JsonNode moved = data(api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", from,
			"to_storage_unit_id", blend, "quantity", 100)));
		assertThat(moved.get("out_movements")).hasSize(2);
		assertThat(moved.get("in_movements")).hasSize(2);
		assertThat(quantity(gemlik)).isEqualByComparingTo("540");
		assertThat(quantity(ayvalik)).isEqualByComparingTo("360");
		assertThat(balance(blend)).isEqualByComparingTo("100");
		assertThat(active(blend)).hasSize(2);
	}

	@Test
	void singleTypeTargetRejectsAMixedTransferAsAWhole() {
		long from = tank(5000, Map.of("allow_multiple_production_product_types", true));
		long single = tank(5000, Map.of());
		long gemlik = materialOf(open(from, GEMLIK, "600", null));
		materialOf(open(from, AYVALIK, "400", null));

		assertRule(api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", from,
			"to_storage_unit_id", single, "quantity", 100)), "Bu tank doluyken farklı ürünler bir arada tutulamaz.");
		assertThat(quantity(gemlik)).isEqualByComparingTo("600");
		assertThat(active(single)).isEmpty();
		assertThat(balance(single)).isZero();
	}

	@Test
	void productionCannotTakeOutOrTransferTheMix() {
		long tank = tank(1000, Map.of());
		long other = tank(1000, Map.of());
		long mix = materialOf(open(tank, GEMLIK, "100", null));
		long process = jdbc.queryForObject("select id from batch_processes order by id limit 1", Long.class);
		String rejected = "Son tanktaki karışım materyalinin partisi yok; üretim içinden üzerinde çıkış ya da "
			+ "transfer yapılamaz.";

		assertThatThrownBy(() -> stock.outbound(tank, mix, BigDecimal.TEN, process, null))
			.isInstanceOf(BusinessRuleException.class).hasMessage(rejected);
		assertThatThrownBy(() -> stock.transfer(tank, other, mix, BigDecimal.TEN, process, null))
			.isInstanceOf(BusinessRuleException.class).hasMessage(rejected);
		assertThat(quantity(mix)).isEqualByComparingTo("100");
	}

	@Test
	void fillingTakesDirectlyFromTheTankMaterialWithoutChildMaterials() {
		long tank = tank(1000, Map.of());
		long mix = materialOf(open(tank, GEMLIK, "100", null));
		long run = jdbc.queryForObject("select id from finished_product_runs order by id limit 1", Long.class);

		stock.outboundForRun(tank, mix, new BigDecimal("40"), "Dolum", run);

		assertThat(quantity(mix)).isEqualByComparingTo("60");
		assertThat(status(mix)).isEqualTo("active");
		assertThat(jdbc.queryForObject("select count(*) from materials where parent_material_id = ?", Long.class, mix))
			.isZero();
	}

	@Test
	void tankAnalysisReplacesTheCalculatedValueAndKeepsTheDeviation() {
		long tank = tank(5000, Map.of());
		long mix = materialOf(open(tank, GEMLIK, "1000", "0.30"));
		open(tank, GEMLIK, "500", "0.50");

		Response measured = api.post("/api/storage-units/" + tank + "/measurements", Map.of("material_id", mix,
			"note", "Lab", "attributes", List.of(Map.of("production_product_attribute_definition_id", ACIDITY,
				"value", new BigDecimal("0.41")))));

		assertThat(measured.status()).as(measured.body()).isEqualTo(201);
		JsonNode row = data(measured).get(0);
		assertThat(row.get("calculated_value").decimalValue()).isEqualByComparingTo("0.3667");
		assertThat(row.get("measured_value").decimalValue()).isEqualByComparingTo("0.41");
		assertThat(row.get("difference").decimalValue()).isEqualByComparingTo("0.0433");
		assertThat(row.get("label").asString()).isEqualTo("Asitlik");
		assertThat(row.get("measured_by").get("name").asString()).isNotBlank();
		assertThat(acidity(mix)).isEqualByComparingTo("0.41");

		// sonraki giris olculen degerden devam eder: (1500 x 0.41 + 500 x 0.30) / 2000 = 0.3825
		open(tank, GEMLIK, "500", "0.30");
		assertThat(acidity(mix)).isEqualByComparingTo("0.3825");

		JsonNode history = data(api.get("/api/storage-units/" + tank + "/measurements"));
		assertThat(history).hasSize(1);
		assertThat(history.get(0).get("note").asString()).isEqualTo("Lab");
	}

	@Test
	void tankAnalysisIsOnlyForNumericValuesOfAFinalTank() {
		long settling = tank(1000, Map.of("kind", "pre_settling"));
		long last = tank(1000, Map.of());
		long inSettling = batchMaterial(GEMLIK, "100", "LITRE", "0.3");
		jdbc.update("update materials set current_location_type = 'storage_unit', current_location_id = ? where id = ?",
			settling, inSettling);
		long mix = materialOf(open(last, GEMLIK, "100", "0.3"));

		assertRule(measure(settling, inSettling, ACIDITY, "0.4"), "Tank analizi yalnızca son tanklarda yapılır.");
		assertRule(measure(last, mix, CULTIVAR, "1"), "Tank analizi yalnızca sayısal özellikler için yapılır.");
		assertRule(measure(last, inSettling, ACIDITY, "0.4"), "Seçilen material bu tankın içinde bulunmuyor.");
	}

	@Test
	void tankAnalysisNeedsTheMeasurePermission() {
		long tank = tank(1000, Map.of());
		long mix = materialOf(open(tank, GEMLIK, "100", "0.3"));
		long planner = jdbc.queryForObject("select id from roles where name = 'planner'", Long.class);

		Response denied = api.as("planner@olivamore.com").post("/api/storage-units/" + tank + "/measurements",
			Map.of("material_id", mix, "attributes", List.of(Map.of("production_product_attribute_definition_id",
				ACIDITY, "value", new BigDecimal("0.4")))));
		jdbc.update("insert into role_permissions (role_id, permission_id) select ?, id from permissions"
			+ " where name = 'storage_unit.measure'", planner);
		try {
			Response allowed = api.as("planner@olivamore.com").post("/api/storage-units/" + tank + "/measurements",
				Map.of("material_id", mix, "attributes", List.of(Map.of("production_product_attribute_definition_id",
					ACIDITY, "value", new BigDecimal("0.4")))));

			assertThat(denied.status()).isEqualTo(403);
			assertThat(allowed.status()).as(allowed.body()).isEqualTo(201);
		}
		finally {
			jdbc.update("delete from role_permissions where role_id = ? and permission_id = "
				+ "(select id from permissions where name = 'storage_unit.measure')", planner);
		}
	}

	@Test
	void lineageWalksFromTheBatchMaterialThroughTheTankMaterial() {
		long tank = tank(1000, Map.of());
		long batch = batchMaterial(GEMLIK, "100", "LITRE", "0.3");
		JsonNode in = data(api.post("/api/storage-movements/inbound",
			Map.of("storage_unit_id", tank, "material_id", batch, "quantity", 40)));
		api.post("/api/storage-movements/inbound", Map.of("storage_unit_id", tank, "material_id", batch, "quantity", 10));
		long mix = in.get("material_id").asLong();

		JsonNode lineage = data(api.get("/api/materials/" + batch + "/lineage"));
		JsonNode fromMix = data(api.get("/api/materials/" + mix + "/lineage"));

		List<String> edges = new ArrayList<>();
		lineage.get("edges").forEach(e -> edges.add(e.get("from").asString() + ">" + e.get("to").asString() + " "
			+ e.get("label").asString() + " " + e.get("quantity").asString()));
		assertThat(edges).containsExactly("material:" + batch + ">material:" + mix + " Tanka girdi 50");
		assertThat(fromMix.get("nodes")).anyMatch(n -> n.get("key").asString().equals("material:" + batch));
		assertThat(fromMix.get("nodes")).anyMatch(n -> n.get("key").asString().equals("material:" + mix)
			&& n.get("lineage_role").asString().equals("tank"));
	}

	// ---- yardimcilar -------------------------------------------------------

	private Response measure(long tank, long material, int definition, String value) {
		return api.post("/api/storage-units/" + tank + "/measurements", Map.of("material_id", material,
			"attributes", List.of(Map.of("production_product_attribute_definition_id", definition,
				"value", new BigDecimal(value)))));
	}

	/** Partide duran materyal (uretimin yazdigi gibi), asitlik degeriyle. */
	private long batchMaterial(int type, String quantity, String unit, String acidity) {
		long batch = jdbc.queryForObject("select id from production_batches order by id limit 1", Long.class);
		long id = jdbc.queryForObject("insert into materials (production_batch_id, production_product_type_id, "
			+ "origin_type, quantity, unit, status, current_location_type, current_location_id, created_at, updated_at) "
			+ "values (?, ?, 'BATCH_PROCESS', ?, ?, 'active', 'production_batch', ?, now(), now()) returning id",
			Long.class, batch, type, new BigDecimal(quantity), unit, batch);
		jdbc.update("insert into material_attribute_values (material_id, production_product_attribute_definition_id, "
			+ "value_decimal, created_at, updated_at) values (?, ?, ?, now(), now())", id, ACIDITY, new BigDecimal(acidity));
		return id;
	}

	private long litreType(String density) {
		int n = codes.incrementAndGet();
		return id(api.post("/api/production-product-types", Map.of("name", "Litre tipi " + n, "code", "L-TYPE-" + n,
			"type", "l_" + n, "unit", "LITRE", "production_product_id", OLIVE_OIL,
			"density_kg_per_litre", new BigDecimal(density))));
	}

	private void allowTypes(long tank, List<? extends Number> types) {
		Response r = api.put("/api/storage-units/" + tank + "/allowed-production-product-types",
			Map.of("production_product_type_ids", types));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	private long tank(int capacity, Map<String, Object> flags) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", "Son tank");
		body.put("code", "FINAL-" + codes.incrementAndGet());
		body.put("capacity", capacity);
		body.put("capacity_unit", "LITRE");
		body.putAll(flags);
		return id(api.post("/api/storage-units", body));
	}

	private static List<Map<String, Object>> attributes(String acidity, String cultivar) {
		return List.of(Map.of("production_product_attribute_definition_id", ACIDITY, "value_decimal",
			new BigDecimal(acidity)), Map.of("production_product_attribute_definition_id", CULTIVAR,
			"value_text", cultivar));
	}

	private Response opening(long tank, long type, String quantity, String unit, List<Map<String, Object>> values) {
		Map<String, Object> body = new HashMap<>();
		body.put("storage_unit_id", tank);
		body.put("production_product_type_id", type);
		body.put("quantity", new BigDecimal(quantity));
		body.put("unit", unit);
		if (values != null) {
			body.put("attribute_values", values);
		}
		return api.post("/api/storage-movements/opening", body);
	}

	private Response open(long tank, long type, String quantity, String acidity) {
		Response r = opening(tank, type, quantity, "LITRE", acidity == null ? null
			: List.of(Map.of("production_product_attribute_definition_id", ACIDITY, "value_decimal",
				new BigDecimal(acidity))));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
		return r;
	}

	private static long materialOf(Response movement) {
		return data(movement).get("material_id").asLong();
	}

	private static void assertRule(Response r, String detail) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).isEqualTo(detail);
	}

	private List<Long> active(long tank) {
		return jdbc.queryForList("select id from materials where current_location_type = 'storage_unit' "
			+ "and current_location_id = ? and status = 'active' order by id", Long.class, tank);
	}

	private BigDecimal quantity(long material) {
		return jdbc.queryForObject("select quantity from materials where id = ?", BigDecimal.class, material);
	}

	private String status(long material) {
		return jdbc.queryForObject("select status from materials where id = ?", String.class, material);
	}

	private BigDecimal acidity(long material) {
		List<BigDecimal> values = jdbc.queryForList("select value_decimal from material_attribute_values "
			+ "where material_id = ? and production_product_attribute_definition_id = ?", BigDecimal.class, material,
			ACIDITY);
		return values.isEmpty() ? null : values.getFirst();
	}

	private String text(long material, int definition) {
		List<String> values = jdbc.queryForList("select value_text from material_attribute_values "
			+ "where material_id = ? and production_product_attribute_definition_id = ?", String.class, material,
			definition);
		return values.isEmpty() ? null : values.getFirst();
	}

	private BigDecimal balance(long tank) {
		return jdbc.queryForObject("select coalesce(sum(current_quantity), 0) from storage_unit_balances"
			+ " where storage_unit_id = ?", BigDecimal.class, tank);
	}

}
