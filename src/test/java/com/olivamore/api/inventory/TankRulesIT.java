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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tank kurallari, her test kendi tankiyla: kapasite, izinli urun ve tip, dolu tankta farkli tip, attribute kurallari
 * (gelen deger ve agirlikli ortalama), transfer, sayim duzeltmesi, tank turu, on dinlendirme tankinda elle hareket
 * yasagi ve birim cevrimi; son tankin tip materyali {@code FinalTankRulesIT}'te, on dinlendirme akisi
 * {@code PreSettlingRulesIT}'te. Demo veri: tip 3
 * Gemlik ve tip 4 Ayvalik zeytinyagi (urun 2, LITRE, kategori yogunlugu 0.916), tip 1 Gemlik zeytini (KG, yogunluk
 * yok), tip 6 baska urun; tanim 4 asitlik.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TankRulesIT {

	private static final int GEMLIK = 3;
	private static final int AYVALIK = 4;
	private static final int OTHER_PRODUCT_TYPE = 6;
	private static final int OLIVE_OIL = 2;
	private static final int ACIDITY = 4;
	private static final int OLIVE_WITHOUT_DENSITY = 1;

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
	void incomingQuantityCannotExceedFreeCapacity() {
		long tank = tank(100, Map.of());
		open(tank, GEMLIK, "60", null);

		Response over = opening(tank, GEMLIK, "40.01", null);
		Response exact = opening(tank, GEMLIK, "40", null);

		assertRule(over, "Hedef tankta yeterli boş kapasite yok.");
		assertThat(exact.status()).as(exact.body()).isEqualTo(201);
	}

	@Test
	void onlyAllowedProductsAndTypesEnter() {
		long tank = tank(1000, Map.of());
		assertThat(api.put("/api/storage-units/" + tank + "/allowed-production-products",
			Map.of("production_product_ids", List.of(OLIVE_OIL))).status()).isEqualTo(200);

		assertRule(opening(tank, OTHER_PRODUCT_TYPE, "10", null), "Seçilen tank bu ürün tipini kabul etmiyor.");

		assertThat(api.put("/api/storage-units/" + tank + "/allowed-production-product-types",
			Map.of("production_product_type_ids", List.of(GEMLIK))).status()).isEqualTo(200);

		assertRule(opening(tank, AYVALIK, "10", null), "Seçilen tank bu spesifik ürünü kabul etmiyor.");
		assertThat(opening(tank, GEMLIK, "10", null).status()).isEqualTo(201);
	}

	@Test
	void filledTankKeepsOneTypeUnlessMultipleTypesAreAllowed() {
		long single = tank(1000, Map.of());
		long mixed = tank(1000, Map.of("allow_multiple_production_product_types", true));
		open(single, GEMLIK, "10", null);
		open(mixed, GEMLIK, "10", null);

		assertRule(opening(single, AYVALIK, "10", null), "Bu tank doluyken farklı ürünler bir arada tutulamaz.");
		assertThat(opening(mixed, AYVALIK, "10", null).status()).isEqualTo(201);
	}

	@Test
	void incomingRuleChecksTheIncomingValue() {
		long tank = tank(1000, Map.of());
		rule(tank, null, "0.5", "incoming_only");

		assertRule(opening(tank, GEMLIK, "10", null),
			"Gelen material üzerinde tank kuralı için gerekli attribute değeri bulunamadı.");
		assertRule(opening(tank, GEMLIK, "10", "0.51"), "Asitlik değeri tank için izin verilen maksimum sınırın üstünde.");
		assertThat(opening(tank, GEMLIK, "10", "0.5").status()).isEqualTo(201);
	}

	@Test
	void averageRuleUsesQuantityWeightedAverage() {
		long tank = tank(5000, Map.of());
		rule(tank, "0.2", "0.5", "tank_average_only");

		// tek basina 0.8 aralik disi, ama ortalamaya girer: (1000 x 0.3 + 500 x 0.8) / 1500 = 0.4667
		assertThat(opening(tank, GEMLIK, "1000", "0.3").status()).isEqualTo(201);
		assertThat(opening(tank, GEMLIK, "500", "0.8").status()).isEqualTo(201);
		// (700 + 1000 x 0.9) / 2500 = 0.64
		assertRule(opening(tank, GEMLIK, "1000", "0.9"),
			"Asitlik ortalaması değeri tank için izin verilen maksimum sınırın üstünde.");
		// ayni deger az miktarda: (700 + 100 x 0.9) / 1600 = 0.4938
		assertThat(opening(tank, GEMLIK, "100", "0.9").status()).isEqualTo(201);
	}

	@Test
	void preSettlingTankTakesNoManualMovements() {
		long settling = tank(1000, Map.of("kind", "pre_settling"));
		long last = tank(1000, Map.of());
		open(last, GEMLIK, "100", null);
		long batchMaterial = jdbc.queryForObject("select id from materials where status = 'active'"
			+ " and current_location_type = 'production_batch' order by id limit 1", Long.class);
		String rejected = "'" + code(settling) + "' bir ön dinlendirme tankı; elle giriş, çıkış, sayım ve transfer "
			+ "yapılamaz. Giriş üretimdeki ön dinlendirme aşamasıyla, çıkış Boşalt ile yapılır.";

		assertRule(opening(settling, GEMLIK, "10", null), rejected);
		assertRule(api.post("/api/storage-movements/inbound",
			Map.of("storage_unit_id", settling, "material_id", batchMaterial, "quantity", 1)), rejected);
		assertRule(api.post("/api/storage-movements/outbound", Map.of("storage_unit_id", settling, "quantity", 1)),
			rejected);
		assertRule(api.post("/api/storage-movements/adjustment", Map.of("storage_unit_id", settling, "quantity", 1,
			"is_increase", false)), rejected);
		assertRule(api.post("/api/storage-movements/adjustment", Map.of("storage_unit_id", settling, "quantity", 1,
			"is_increase", true, "production_product_type_id", GEMLIK, "unit", "LITRE")), rejected);
		assertRule(api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", last,
			"to_storage_unit_id", settling, "quantity", 10)), rejected);
		assertRule(api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", settling,
			"to_storage_unit_id", last, "quantity", 10)), rejected);
		assertThat(balance(settling)).isZero();
		assertThat(balance(last)).isEqualByComparingTo("100");
	}

	@Test
	void preSettlingTankWorksWithASingleType() {
		Response blend = api.post("/api/storage-units", Map.of("name", "Dinlenme", "code", "RULE-"
			+ codes.incrementAndGet(), "capacity", 1000, "capacity_unit", "LITRE", "kind", "pre_settling",
			"allow_multiple_production_product_types", true));
		long tank = tank(1000, Map.of("is_blend_tank", true));
		Response toPreSettling = api.patch("/api/storage-units/" + tank, Map.of("kind", "pre_settling"));
		Response withFlagsOff = api.patch("/api/storage-units/" + tank, Map.of("kind", "pre_settling",
			"is_blend_tank", false));
		String rejected = "Ön dinlendirme tankı tek ürün tipiyle çalışır; blend ve birden çok ürün ya da ürün tipi "
			+ "ayarları açılamaz.";

		assertRule(blend, rejected);
		assertRule(toPreSettling, rejected);
		assertThat(withFlagsOff.status()).as(withFlagsOff.body()).isEqualTo(200);
		assertRule(api.patch("/api/storage-units/" + tank, Map.of("allow_multiple_production_products", true)),
			rejected);
	}

	@Test
	void outboundCannotTakeMoreThanTheTankHas() {
		long last = tank(1000, Map.of());
		open(last, GEMLIK, "100", null);

		assertRule(api.post("/api/storage-movements/outbound", Map.of("storage_unit_id", last, "quantity", "100.0001")),
			"Tankta yeterli miktar bulunmuyor.");
	}

	@Test
	void transferMovesAPortionIntoTheTargetTankMaterial() {
		long from = tank(1000, Map.of());
		long to = tank(1000, Map.of());
		long material = materialOf(open(from, GEMLIK, "100", "0.3"));

		JsonNode transfer = data(api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", from,
			"to_storage_unit_id", to, "quantity", "30.5")));

		JsonNode out = transfer.get("out_movements").get(0);
		JsonNode in = transfer.get("in_movements").get(0);
		assertThat(out.get("movement_type").asString()).isEqualTo("TRANSFER_OUT");
		assertThat(out.get("material_id").asLong()).isEqualTo(material);
		assertThat(in.get("movement_type").asString()).isEqualTo("TRANSFER_IN");
		assertThat(in.get("storage_unit_id").asLong()).isEqualTo(to);
		assertThat(in.get("source_material_id").asLong()).isEqualTo(material);
		assertThat(quantity(material)).isEqualByComparingTo("69.5");
		assertThat(balance(from)).isEqualByComparingTo("69.5");
		assertThat(balance(to)).isEqualByComparingTo("30.5");
		// hedefin tank materyali kaynagin attribute degerini alir
		long moved = in.get("material_id").asLong();
		assertThat(jdbc.queryForObject("select value_decimal from material_attribute_values where material_id = ?",
			BigDecimal.class, moved)).isEqualByComparingTo("0.3");
	}

	@Test
	void transferToTheSameTankIsRejected() {
		long tank = tank(1000, Map.of());
		long material = materialOf(open(tank, GEMLIK, "100", null));

		Response same = api.post("/api/storage-movements/transfer", Map.of("from_storage_unit_id", tank,
			"to_storage_unit_id", tank, "material_id", material, "quantity", 1));

		assertThat(same.status()).isEqualTo(422);
		assertThat(same.json().get("errors").get("to_storage_unit_id").get(0).asString())
			.isEqualTo("The to storage unit id field and from storage unit id must be different.");
	}

	@Test
	void countAdjustmentChangesTheTankMaterialOfAFinalTank() {
		long tank = tank(1000, Map.of());
		long material = materialOf(open(tank, GEMLIK, "100", null));

		JsonNode decrease = data(api.post("/api/storage-movements/adjustment",
			Map.of("storage_unit_id", tank, "quantity", 20, "is_increase", false))).get(0);
		JsonNode increase = data(api.post("/api/storage-movements/adjustment", Map.of("storage_unit_id", tank,
			"quantity", 5, "is_increase", true, "production_product_type_id", GEMLIK, "unit", "LITRE"))).get(0);

		assertThat(decrease.get("movement_type").asString()).isEqualTo("ADJUSTMENT_OUT");
		assertThat(decrease.get("material_id").asLong()).isEqualTo(material);
		assertThat(increase.get("material_id").asLong()).isEqualTo(material);
		assertThat(quantity(material)).isEqualByComparingTo("85");
		assertThat(balance(tank)).isEqualByComparingTo("85");
	}

	@Test
	void countIncreaseNeedsTypeAndUnit() {
		long tank = tank(1000, Map.of());

		Response r = api.post("/api/storage-movements/adjustment",
			Map.of("storage_unit_id", tank, "quantity", 5, "is_increase", true));

		assertThat(r.status()).isEqualTo(422);
		assertThat(r.json().get("errors").properties()).extracting(Map.Entry::getKey)
			.containsExactlyInAnyOrder("production_product_type_id", "unit");
	}

	@Test
	void kindChangesOnlyWhileTheTankIsEmpty() {
		long tank = tank(1000, Map.of());
		assertThat(data(api.get("/api/storage-units/" + tank)).get("kind").asString()).isEqualTo("final");

		assertThat(api.patch("/api/storage-units/" + tank, Map.of("kind", "pre_settling")).status()).isEqualTo(200);
		assertThat(api.patch("/api/storage-units/" + tank, Map.of("kind", "final")).status()).isEqualTo(200);
		open(tank, GEMLIK, "10", null);

		Response full = api.patch("/api/storage-units/" + tank, Map.of("kind", "pre_settling"));
		Response same = api.patch("/api/storage-units/" + tank, Map.of("kind", "final"));

		assertRule(full, "'" + code(tank) + "' tankında 1 aktif materyal var; tank türü yalnızca tank boşken "
			+ "değiştirilebilir.");
		assertThat(same.status()).as(same.body()).isEqualTo(200);
		assertThat(data(api.get("/api/storage-units/" + tank)).get("kind").asString()).isEqualTo("final");
	}

	@Test
	void kilogramsEnterALitreTankConvertedWithTheTypeDensity() {
		long tank = tank(100, Map.of("allow_multiple_production_product_types", true));
		long kgType = kgType("0.9");
		open(tank, GEMLIK, "10", null);

		// 81 kg / 0.9 = 90 L: 10 + 90 = 100 L, tam dolu
		Response exact = opening(tank, kgType, "81", "KG", null);
		Response over = opening(tank, kgType, "0.01", "KG", null);

		assertThat(exact.status()).as(exact.body()).isEqualTo(201);
		assertRule(over, "Hedef tankta yeterli boş kapasite yok.");
	}

	@Test
	void typeWithoutDensityCannotEnterAnyTankEvenInTheSameUnit() {
		long litreTank = tank(1000, Map.of());
		long kgTank = tank(1000, Map.of("capacity_unit", "KG"));
		String rejected = "Gemlik Zeytini için yoğunluk tanımlı değil; yoğunluğu bilinmeyen ürün tanka giremez. "
			+ "Ürün tipine yoğunluk (kg/L) girilmeli.";

		assertRule(opening(litreTank, OLIVE_WITHOUT_DENSITY, "10", "KG", null), rejected);
		assertRule(opening(kgTank, OLIVE_WITHOUT_DENSITY, "10", "KG", null), rejected);
		assertRule(api.post("/api/storage-movements/adjustment", Map.of("storage_unit_id", kgTank, "quantity", 5,
			"is_increase", true, "production_product_type_id", OLIVE_WITHOUT_DENSITY, "unit", "KG")), rejected);
		assertThat(balance(kgTank)).isZero();
	}

	@Test
	void emptyTankUsesTheProductDefaultDensityOnlyForASingleProduct() {
		long ownDensity = tank(1000, Map.of());
		long twoProducts = tank(1000, Map.of());
		allowTypes(ownDensity, List.of(kgType("0.9")));
		allowTypes(twoProducts, List.of(GEMLIK, OTHER_PRODUCT_TYPE));

		// tipin kendi yogunlugu (0.9) degil, kategorinin varsayilani (0.916)
		assertThat(data(api.get("/api/storage-units/" + ownDensity)).get("capacity_kg").asLong()).isEqualTo(916);
		assertThat(data(api.get("/api/storage-units/" + twoProducts)).get("capacity_kg").isNull()).isTrue();
	}

	@Test
	void capacityAndContentAreShownInBothUnits() {
		long unrestricted = tank(1000, Map.of());
		long gemlikOnly = tank(1000, Map.of());
		assertThat(api.put("/api/storage-units/" + gemlikOnly + "/allowed-production-product-types",
			Map.of("production_product_type_ids", List.of(GEMLIK))).status()).isEqualTo(200);

		// bos tank: izinli liste yoksa kapasitenin kg karsiligi bilinmez, varsa tipin yogunlugundan
		JsonNode empty = data(api.get("/api/storage-units/" + unrestricted));
		JsonNode allowed = data(api.get("/api/storage-units/" + gemlikOnly));
		assertThat(empty.get("capacity_litre").asLong()).isEqualTo(1000);
		assertThat(empty.get("capacity_kg").isNull()).isTrue();
		assertThat(empty.get("filled_kg").asLong()).isZero();
		assertThat(allowed.get("capacity_kg").asLong()).isEqualTo(916);

		open(unrestricted, GEMLIK, "100", null);
		JsonNode filled = data(api.get("/api/storage-units/" + unrestricted));
		assertThat(filled.get("filled_quantity").asLong()).isEqualTo(100);
		assertThat(filled.get("filled_litre").asLong()).isEqualTo(100);
		assertThat(filled.get("filled_kg").decimalValue()).isEqualByComparingTo("91.6");
		assertThat(filled.get("capacity_kg").asLong()).isEqualTo(916);
	}

	private long kgType(String density) {
		int n = codes.incrementAndGet();
		return id(api.post("/api/production-product-types", Map.of("name", "Kg tipi " + n, "code", "KG-TYPE-" + n,
			"type", "kg_" + n, "unit", "KG", "production_product_id", OLIVE_OIL,
			"density_kg_per_litre", new BigDecimal(density))));
	}

	private void allowTypes(long tank, List<? extends Number> types) {
		Response r = api.put("/api/storage-units/" + tank + "/allowed-production-product-types",
			Map.of("production_product_type_ids", types));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	private String code(long tank) {
		return jdbc.queryForObject("select code from storage_units where id = ?", String.class, tank);
	}

	// ---- yardimcilar -------------------------------------------------------

	private long tank(int capacity, Map<String, Object> flags) {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("name", "Kural tanki");
		body.put("code", "RULE-" + codes.incrementAndGet());
		body.put("capacity", capacity);
		body.put("capacity_unit", "LITRE");
		body.putAll(flags);
		return id(api.post("/api/storage-units", body));
	}

	private void rule(long tank, String min, String max, String mode) {
		Map<String, Object> rule = new java.util.HashMap<>();
		rule.put("production_product_attribute_definition_id", ACIDITY);
		rule.put("min_value_decimal", min);
		rule.put("max_value_decimal", max);
		rule.put("apply_mode", mode);
		Response r = api.put("/api/storage-units/" + tank + "/attribute-rules", Map.of("rules", List.of(rule)));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	private Response opening(long tank, int type, String quantity, String acidity) {
		return opening(tank, type, quantity, "LITRE", acidity);
	}

	private Response opening(long tank, long type, String quantity, String unit, String acidity) {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("storage_unit_id", tank);
		body.put("production_product_type_id", type);
		body.put("quantity", new BigDecimal(quantity));
		body.put("unit", unit);
		if (acidity != null) {
			body.put("attribute_values", List.of(Map.of("production_product_attribute_definition_id", ACIDITY,
				"value_decimal", new BigDecimal(acidity))));
		}
		return api.post("/api/storage-movements/opening", body);
	}

	private Response open(long tank, int type, String quantity, String acidity) {
		Response r = opening(tank, type, quantity, acidity);
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

	private BigDecimal quantity(long material) {
		return jdbc.queryForObject("select quantity from materials where id = ?", BigDecimal.class, material);
	}

	private BigDecimal balance(long tank) {
		return jdbc.queryForObject("select coalesce(sum(current_quantity), 0) from storage_unit_balances"
			+ " where storage_unit_id = ?", BigDecimal.class, tank);
	}

}
