package com.olivamore.api.devdata;

import com.olivamore.api.shared.time.Timestamps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Demo fabrikanin bir yili, gun gun ve API uzerinden: zeytin partileri asamalardan gecer, yag on dinlendirme tankinda
 * dinlenir, Boşalt ile son tanklara aktarilir (fark tortu olur), son tanklardan dolum yapilir, siseler depoya girer,
 * siparisler hazirlanip sevk edilir. Her islem uygulamanin kendi kurallarindan gecer; saat ({@link Timestamps}) her
 * islemden once simule edilen ana ayarlanir. Ayni gun ve ayni tohumla her calisma ayni veriyi uretir.
 * <p>
 * Asitlik mevsime baglidir: hasatta (ekim-subat) Gemlik 0.3, Ayvalik 0.4 civarinda; hasat disinda Gemlik 0.8,
 * Ayvalik 0.95 civarinda. Yag asitligine gore ayri tanklarda toplanir; Klasik Blend hasat Gemlik'i (rezerv tank) ile
 * hasat disi Ayvalik'tan yapilir. Tankta yer yoksa o gun zeytin kabul edilmez.
 */
@Slf4j
final class DemoFactory {

	private static final String PRODUCTION = "uretim@olivamore.com";
	private static final List<String> OPERATORS =
		List.of(PRODUCTION, "operator1@olivamore.com", "operator2@olivamore.com");
	private static final String ADMIN = "admin@olivamore.com";
	private static final String WAREHOUSE = "depo@olivamore.com";
	private static final String PLANNER = "planner@olivamore.com";

	/** Son gun bu saatten sonra islem yapilmaz: demo verinin en yeni ani. */
	private static final LocalTime CUTOFF = LocalTime.of(15, 30);
	/** Materyalin yogunlugu (kg/L): tipin kendisi, yoksa urunun varsayilani. {@code m}, {@code t}, {@code p} takma adlari. */
	private static final String DENSITY = "coalesce(nullif(t.density_kg_per_litre, 0), p.default_density_kg_per_litre)";
	private static final String MATERIAL_TYPE = "join production_product_types t on t.id = m.production_product_type_id "
		+ "join production_products p on p.id = t.production_product_id";
	/** On dinlendirme tankina giren en buyuk parti yagi (L): yer yoksa zeytin kabul edilmez. */
	private static final BigDecimal BATCH_ROOM = new BigDecimal("2000");
	private static final BigDecimal TANK_MARGIN = new BigDecimal("50");
	private static final BigDecimal BLEND_GEMLIK = new BigDecimal("0.7");
	private static final BigDecimal BLEND_AYVALIK = new BigDecimal("0.3");
	private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
	private static final List<String> FILTERS = List.of("Kağıt filtre", "Pamuk filtre", "Kartuş filtre");
	/** Acilis stogu: tank, urun tipi, litre, asitlik. */
	private static final List<String[]> OPENING_STOCK = List.of(
		new String[] {"FILL-GEM-03", "OLIVE_OIL_GEMLIK", "2950", "0.30"},
		new String[] {"FILL-GEM-08", "OLIVE_OIL_GEMLIK", "1975", "0.78"},
		new String[] {"FILL-AVL-04", "OLIVE_OIL_AYVALIK", "1950", "0.42"},
		new String[] {"FILL-AVL-09", "OLIVE_OIL_AYVALIK", "975", "0.88"},
		new String[] {"FILL-BLEND-50", "OLIVE_OIL_GEMLIK", "630", "0.47"},
		new String[] {"FILL-BLEND-50", "OLIVE_OIL_AYVALIK", "270", "0.58"},
		new String[] {"FILL-ORANGE-60", "OLIVE_OIL_GEMLIK", "780", "0.60"},
		new String[] {"FILL-ORANGE-60", "ORANGE", "195", "0.40"});

	private final DemoApi api;
	private final JdbcTemplate jdbc;
	private final Random random;
	private final LocalDate firstDay;
	private final LocalDate lastDay;
	private final Ids ids;

	private final Map<Cultivar, Resting> resting = new LinkedHashMap<>();
	private final List<PendingOrder> pendingOrders = new ArrayList<>();
	private final Map<Long, Long> reserved = new HashMap<>();
	private LocalDateTime now;
	private int refused;

	DemoFactory(DemoApi api, JdbcTemplate jdbc, long seed, LocalDate firstDay, LocalDate lastDay) {
		this.api = api;
		this.jdbc = jdbc;
		this.random = new Random(seed);
		this.firstDay = firstDay;
		this.lastDay = lastDay;
		this.ids = new Ids(jdbc);
		for (Cultivar cultivar : Cultivar.values()) {
			resting.put(cultivar, new Resting());
		}
	}

	void run() {
		at(firstDay.atTime(8, 0));
		openingStock();
		openingPackaging();
		for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
			simulate(day);
		}
		log.info("Demo akis: {} - {}, yer olmadigi icin kabul edilmeyen parti: {}", firstDay, lastDay, refused);
	}

	private void simulate(LocalDate day) {
		at(day.atTime(6, 30));
		for (Cultivar cultivar : Cultivar.values()) {
			Resting tank = resting.get(cultivar);
			if (!tank.entries.isEmpty() && (!tank.entries.getFirst().day.isAfter(day.minusDays(3))
				|| stock(ids.tank(cultivar.restingTank)).compareTo(new BigDecimal("4000")) >= 0)) {
				empty(cultivar);
			}
		}
		if (at(day.atTime(6, 50))) {
			transfers();
		}
		if (at(day.atTime(7, 0))) {
			int arrivals = arrivals(day);
			for (int i = 1; i <= arrivals && intake(day, i); i++) {
				later(10, 40);
			}
			if (day.equals(lastDay)) {
				registerOnly(day, arrivals + 1);
			}
		}
		if (at(day.atTime(8, 0))) {
			advanceOrders(day);
			fill(day);
		}
		if (at(day.atTime(10, 0))) {
			if (day.equals(lastDay)) {
				closingOrders(day);
			}
			else {
				newOrders(day);
			}
		}
		if (day.getDayOfMonth() == 1 && at(day.atTime(14, 0))) {
			measureTanks();
		}
		if (day.getDayOfMonth() == 15 && at(day.atTime(14, 30))) {
			scrapPackaging();
		}
	}

	// ---- saat ---------------------------------------------------------------------------

	/** Simule edilen ani ayarlar; son gunun kapanisindan sonraysa false (islem yapilmaz). */
	private boolean at(LocalDateTime time) {
		now = time;
		if (time.toLocalDate().isAfter(lastDay) || time.toLocalDate().equals(lastDay)
			&& time.toLocalTime().isAfter(CUTOFF)) {
			return false;
		}
		Timestamps.useClock(Clock.fixed(time.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
		return true;
	}

	private boolean later(int minMinutes, int maxMinutes) {
		return at(now.plusMinutes(between(minMinutes, maxMinutes)));
	}

	// ---- zeytin kabulu ve asamalar --------------------------------------------------------

	/** Hasatta gunde 1-2, hasat disinda birkac gunde bir parti. */
	private int arrivals(LocalDate day) {
		double expected = Season.of(day) == Season.HARVEST ? 1.6 : 0.5;
		return (int) expected + (random.nextDouble() < expected % 1 ? 1 : 0);
	}

	/** Bir parti: kantardan on dinlendirme tankina kadar. Tankta yer yoksa parti kabul edilmez (false). */
	private boolean intake(LocalDate day, int sequence) {
		Season season = Season.of(day);
		Cultivar cultivar = random.nextDouble() < (season == Season.HARVEST ? 0.62 : 0.8)
			? Cultivar.GEMLIK : Cultivar.AYVALIK;
		Resting tank = resting.get(cultivar);
		long tankId = ids.tank(cultivar.restingTank);
		if (tank.season != null && tank.season != season && !empty(cultivar)) {
			return true;
		}
		if (!fits(cultivar, season) && (!empty(cultivar) || !fits(cultivar, season))) {
			refused++;
			return true;
		}
		Batch batch = register(day, sequence, cultivar, true);
		if (batch == null || !walk(batch, season)) {
			return false;
		}
		tank.season = season;
		tank.entries.add(batch);
		return true;
	}

	/** Kilo verilirse (kantarda tartildi) kantar asamasi o kiloyla tamamlanir. */
	private Batch register(LocalDate day, int sequence, Cultivar cultivar, boolean weighed) {
		if (!later(0, 10)) {
			return null;
		}
		List<Long> lines = cultivar == Cultivar.GEMLIK ? List.of(ids.line(1), ids.line(2)) : List.of(ids.line(1), ids.line(3));
		long line = lines.get(random.nextInt(lines.size()));
		Ids.SupplierField source = ids.suppliers().get(random.nextInt(ids.suppliers().size()));
		BigDecimal kg = BigDecimal.valueOf(between(400, 1800) * 5L);
		Map<String, Object> body = body("batch_code", "P-" + day.format(CODE_DATE) + "-" + "%02d".formatted(sequence),
			"production_line_id", line, "input_production_product_type_ids", List.of(ids.type(cultivar.olive)),
			"supplier_id", source.supplierId(), "supplier_field_id", random.nextDouble() < 0.7 ? source.fieldId() : null,
			"quantity_kg", weighed ? kg : null, "truck_plate", plate(),
			"weighing_slip_no", "TF-" + day.format(CODE_DATE) + between(100, 999));
		long id = DemoApi.id(api.post(PRODUCTION, "/api/production-batches", body));
		purchase(id);
		return new Batch(id, day, sequence, cultivar, ids.stages(line), kg);
	}

	/**
	 * Alim bilgisi (yonetici girer): cogu ciftciden, bir kismi tuccardan (nakliyeli), az kismi kendi bahcemizden
	 * (fiyatsiz). Ayri, partiye bagli bir rastgele kaynak: ana akisin sayilari kaymaz.
	 */
	private void purchase(long batchId) {
		Random own = new Random(batchId * 7919L);
		double draw = own.nextDouble();
		String channel = draw < 0.6 ? "farmer" : draw < 0.9 ? "trader" : "own_grove";
		boolean priced = !"own_grove".equals(channel);
		BigDecimal price = priced ? BigDecimal.valueOf(3000 + own.nextInt(1500), 2) : null;
		BigDecimal transport = "trader".equals(channel) ? BigDecimal.valueOf((5 + own.nextInt(20)) * 100L) : null;
		api.put(ADMIN, "/api/production-batches/" + batchId + "/purchase", body("supply_channel", channel,
			"price_per_kg", price, "vat_rate", priced ? BigDecimal.ONE : null, "transport_cost", transport,
			"price_note", priced && own.nextDouble() < 0.3 ? "Hasat sonu ödeme" : null));
	}

	/** Son gun kayit acilir ama tartim yapilmaz: bekleyen parti. */
	private void registerOnly(LocalDate day, int sequence) {
		if (at(day.atTime(15, 0))) {
			register(day, sequence, Cultivar.GEMLIK, false);
		}
	}

	/**
	 * Kantardan on dinlendirmeye: her asama gercek veriyle; kapanis saatine gelinirse yarida kalir (false). Kantar
	 * kayitta tartildi; Ikinci Tartim'dan sonra zeytinin hepsi tek gruba ayrilir, makineli asamalar grupta.
	 */
	private boolean walk(Batch batch, Season season) {
		String operator = OPERATORS.get(random.nextInt(OPERATORS.size()));
		List<Long> stages = batch.stages;
		if (!later(5, 20)) {
			return false;
		}

		BigDecimal foreign = scale(batch.kg.multiply(decimal(0.004, 0.015)), 1);
		if (!later(25, 50)) {
			return false;
		}
		complete(operator, batch, stages.get(1), null);

		BigDecimal sorted = scale(batch.kg.subtract(foreign), 1);
		if (!later(10, 25)) {
			return false;
		}
		complete(operator, batch, stages.get(2), body("quantity", sorted));
		batch.group(DemoApi.id(api.post(PRODUCTION, "/api/production-batches/" + batch.id + "/groups",
			body("all_remaining", true))));
		if (!later(10, 20)) {
			return false;
		}
		complete(operator, batch, stages.get(3), null);

		// Pirina kalan ciktidir (zeytin - yag); arada bir tahmini elle biraz dusurulur (sapma orani)
		BigDecimal oil = scale(sorted.multiply(decimal(0.15, 0.205)), 2);
		BigDecimal pomace = sorted.subtract(oil);
		if (random.nextDouble() < 0.2) {
			pomace = scale(pomace.multiply(decimal(0.97, 0.995)), 1);
		}
		if (!pressing(operator, batch, stages.get(4), sorted)) {
			return false;
		}
		fields(operator, batch, stages.get(4), Map.of(ids.field("zeytin_giris_sicakligi"), tenth(8, 18).toPlainString(),
			ids.field("ortam_sicakligi"), tenth(10, 22).toPlainString()));
		complete(operator, batch, stages.get(4), body("input_quantity", sorted, "outputs", List.of(
			body("product_type_key", "olive_oil", "quantity", oil),
			body("production_product_id", ids.pomaceProduct(), "quantity", pomace, "unit", "KG"))));

		batch.oil = oil;

		BigDecimal acidity = batch.cultivar.acidity(season, this);
		if (!later(30, 90)) {
			return false;
		}
		fields(operator, batch, stages.get(5), Map.of(ids.field("numune_no"),
			"LAB-" + batch.day.format(CODE_DATE) + "-" + "%02d".formatted(batch.sequence)));
		complete(operator, batch, stages.get(5), body("attributes",
			List.of(body("attribute_key", "acidity", "value", acidity))));

		if (!later(10, 30)) {
			return false;
		}
		complete(operator, batch, stages.get(6), body("quantity", batch.oil,
			"storage_unit_id", ids.tank(batch.cultivar.restingTank)));
		if (!later(15, 45)) {
			return false;
		}
		api.post(ADMIN, "/api/batch-processes/" + process(batch, stages.get(6)) + "/approve", Map.of());
		return true;
	}

	/** Boşalt'tan sonra: filtreleme tankta kapandi, kalan randiman. */
	private void finish(Batch batch) {
		String operator = OPERATORS.get(random.nextInt(OPERATORS.size()));
		if (!later(10, 20)) {
			return;
		}
		// Randiman sunucuda hesaplanir; arada bir laboratuvar sonucuyla elle biraz duzeltilir (sapma)
		long stage = process(batch, batch.stages.get(8));
		JsonNode calculated = api.get(operator, "/api/batch-processes/" + stage + "/yield").get("data")
			.get("yield_percent");
		BigDecimal yield = calculated == null || calculated.isNull() ? null : calculated.decimalValue();
		if (yield == null) {
			return;
		}
		if (random.nextDouble() < 0.15) {
			yield = yield.add(decimal(-0.4, 0.4)).setScale(2, RoundingMode.HALF_UP);
		}
		complete(operator, batch, batch.stages.get(8), body("yield_percent", yield));
		if (later(15, 60)) {
			api.post(ADMIN, "/api/batch-processes/" + process(batch, batch.stages.get(8)) + "/approve", Map.of());
		}
	}

	private void fields(String user, Batch batch, long stage, Map<Long, String> values) {
		List<Map<String, Object>> fields = values.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> body("process_type_field_id", e.getKey(), "value", e.getValue())).toList();
		api.post(user, "/api/batch-processes/" + process(batch, stage) + "/sync-field-values", body("fields", fields));
	}

	private void complete(String user, Batch batch, long stage, Map<String, Object> data) {
		api.patch(user, "/api/batch-processes/" + process(batch, stage),
			data == null ? body("status", "completed") : body("status", "completed", "operation_data", data));
	}

	/**
	 * Sikim: kronometre calisirken parti sirayla Kirici, Malaksor, Dekantor, Separator'dan gecer. Akis surekli
	 * oldugu icin bir sonraki makine oncekisi bitmeden baslar; malaksor 30-50 dk.
	 */
	private boolean pressing(String operator, Batch batch, long stage, BigDecimal kg) {
		String steps = "/api/batch-processes/" + process(batch, stage) + "/machine-runs/";
		machine(operator, batch, stage, "start");
		api.post(operator, steps + "1/start", Map.of());
		if (!later(2, 4)) {
			return false;
		}
		api.post(operator, steps + "2/start", Map.of());
		if (!later(12, 20)) {
			return false;
		}
		api.post(operator, steps + "1/finish", body("values", body("elek_capi", between(5, 7),
			"devir", between(2800, 3000), "hamur_sicakligi", tenth(22, 27), "besleme_debisi", between(1800, 2400))));
		if (!later(18, 30)) {
			return false;
		}
		api.post(operator, steps + "2/finish", body("values", body("sicaklik", tenth(25, 28.5),
			"devir", between(18, 25), "inert_gaz", random.nextDouble() < 0.2, "ceket_suyu_sicakligi", tenth(30, 38),
			"hazne_dolulugu", kg, "mikro_talk", random.nextDouble() < 0.25 ? tenth(0.5, 2) : null)));
		api.post(operator, steps + "3/start", Map.of());
		if (!later(2, 4)) {
			return false;
		}
		api.post(operator, steps + "4/start", Map.of());
		if (!later(15, 25)) {
			return false;
		}
		api.post(operator, steps + "3/finish", body("values", body("devir", between(3000, 3400),
			"debi", between(1800, 2200), "su_ilavesi", tenth(0, 8), "diferansiyel_devir", tenth(8, 15),
			"su_sicakligi", tenth(25, 30), "yag_cikis_sicakligi", tenth(24, 28))));
		if (!later(2, 5)) {
			return false;
		}
		api.post(operator, steps + "4/finish", body("values", body("devir", between(6500, 7200),
			"yikama_suyu_sicakligi", tenth(25, 35), "su_ilavesi", between(100, 300),
			"yag_cikis_sicakligi", tenth(24, 28), "bosaltma_araligi", between(15, 30))));
		if (!later(1, 3)) {
			return false;
		}
		machine(operator, batch, stage, "finish");
		return true;
	}

	/** Kronometreli asamada makineyi baslatir ya da bitirir ({@code start} / {@code finish}). */
	private void machine(String user, Batch batch, long stage, String action) {
		api.post(user, "/api/batch-processes/" + process(batch, stage) + "/machine/" + action, Map.of());
	}

	private long process(Batch batch, long stage) {
		return jdbc.queryForObject("select id from batch_processes where production_batch_id = ? and production_stage_id = ?",
			Long.class, batch.id, stage);
	}

	// ---- on dinlendirme: Boşalt ------------------------------------------------------------

	/** On dinlendirme tankini bosaltir; hedeflerde yer yoksa bosaltmaz (false). */
	private boolean empty(Cultivar cultivar) {
		Resting tank = resting.get(cultivar);
		if (tank.entries.isEmpty()) {
			return true;
		}
		long tankId = ids.tank(cultivar.restingTank);
		BigDecimal withdrawn = scale(stock(tankId).multiply(decimal(0.975, 0.99)), 2);
		Map<Long, BigDecimal> targets = targets(cultivar, tank.season, withdrawn);
		if (targets == null || !later(0, 5)) {
			return false;
		}
		// Filtreleme tankta: filtre tipi gruplarin filtreleme asamasina yazilir, kronometre tanktan baslar,
		// Boşalt bitirir. Gun bitse de bosaltilir: filtreleme surerken tanka giris olmaz.
		String filter = FILTERS.get(random.nextInt(FILTERS.size()));
		for (Batch batch : tank.entries) {
			fields(PRODUCTION, batch, batch.stages.get(7), Map.of(ids.field("filtre_tipi"), filter));
		}
		api.post(PRODUCTION, "/api/storage-units/" + tankId + "/filtering/start", Map.of());
		later(20, 40);
		api.post(PRODUCTION, "/api/storage-units/" + tankId + "/empty", body("targets",
			targets.entrySet().stream().map(e -> body("storage_unit_id", e.getKey(), "quantity", e.getValue())).toList(),
			"note", "Dinlenme tamamlandı"));
		BigDecimal toBlend = targets.get(ids.tank("DOLUM-BLEND-01"));
		if (toBlend != null && later(10, 20)) {
			transfer(ids.tank("REZERV-GEM-01"), ids.tank("DOLUM-BLEND-01"),
				scale(toBlend.multiply(BLEND_GEMLIK).divide(BLEND_AYVALIK, 6, RoundingMode.HALF_UP), 2));
		}
		for (Batch batch : tank.entries) {
			finish(batch);
		}
		tank.entries.clear();
		tank.season = null;
		return true;
	}

	/**
	 * Yeni parti sigar mi: on dinlendirme tankinda yer olmali ve tank bosaltildiginda butun yag hedef tanklara
	 * sigmali (yoksa tank bosaltilamadan dolar ve kilitlenir).
	 */
	private boolean fits(Cultivar cultivar, Season season) {
		long tankId = ids.tank(cultivar.restingTank);
		BigDecimal content = stock(tankId).add(BATCH_ROOM);
		BigDecimal targetRoom = rooms(cultivar, season).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return room(tankId).compareTo(BATCH_ROOM) >= 0 && content.compareTo(targetRoom) <= 0;
	}

	/** Bosaltilan yagin gidecegi son tanklar, sirayla bos yerleri kadar; hepsi sigmiyorsa null. */
	private Map<Long, BigDecimal> targets(Cultivar cultivar, Season season, BigDecimal withdrawn) {
		Map<Long, BigDecimal> rooms = rooms(cultivar, season);
		Map<Long, BigDecimal> targets = new LinkedHashMap<>();
		BigDecimal left = withdrawn;
		for (Map.Entry<Long, BigDecimal> room : rooms.entrySet()) {
			BigDecimal take = left.min(room.getValue());
			if (take.compareTo(BigDecimal.ONE) >= 0) {
				targets.put(room.getKey(), take);
				left = left.subtract(take);
			}
		}
		return left.signum() > 0 ? null : targets;
	}

	/** Mevsimin yaginin gidebilecegi son tanklar ve bos yerleri, oncelik sirasiyla. */
	private Map<Long, BigDecimal> rooms(Cultivar cultivar, Season season) {
		Map<Long, BigDecimal> rooms = new LinkedHashMap<>();
		switch (cultivar) {
			case GEMLIK -> {
				if (season == Season.HARVEST) {
					rooms.put(ids.tank("TOPLAMA-GEM-01"), room(ids.tank("TOPLAMA-GEM-01")));
					rooms.put(ids.tank("REZERV-GEM-01"), room(ids.tank("REZERV-GEM-01")));
				}
				else {
					rooms.put(ids.tank("FILL-GEM-11"), room(ids.tank("FILL-GEM-11")));
				}
			}
			case AYVALIK -> {
				if (season == Season.HARVEST) {
					rooms.put(ids.tank("TOPLAMA-AYV-01"), room(ids.tank("TOPLAMA-AYV-01")));
				}
				else {
					long blend = ids.tank("DOLUM-BLEND-01");
					BigDecimal reserve = stock(ids.tank("REZERV-GEM-01")).multiply(BLEND_AYVALIK)
						.divide(BLEND_GEMLIK, 2, RoundingMode.DOWN);
					BigDecimal blendRoom = room(blend).multiply(BLEND_AYVALIK).setScale(2, RoundingMode.DOWN);
					rooms.put(blend, reserve.min(blendRoom));
				}
			}
		}
		return rooms;
	}

	// ---- son tanklar ------------------------------------------------------------------------

	/** Toplama tanklarindan dolum tanklarina: dolum tanki azaldikca. */
	private void transfers() {
		topUp("TOPLAMA-GEM-01", "DOLUM-GEM-01", 4000);
		reserve();
		topUp("TOPLAMA-AYV-01", "DOLUM-AYV-01", 4000);
	}

	/** Hasat Gemlik'inin fazlasi rezerv tanka: hasat disinda Klasik Blend'in Gemlik payi buradan gelir. */
	private void reserve() {
		long from = ids.tank("TOPLAMA-GEM-01");
		long to = ids.tank("REZERV-GEM-01");
		BigDecimal surplus = stock(from).subtract(new BigDecimal("2000"));
		BigDecimal quantity = scale(surplus.min(room(to)), 2);
		if (quantity.compareTo(new BigDecimal("100")) >= 0 && later(5, 20)) {
			transfer(from, to, quantity);
		}
	}

	private void topUp(String fromCode, String toCode, int below) {
		long from = ids.tank(fromCode);
		long to = ids.tank(toCode);
		if (stock(to).compareTo(BigDecimal.valueOf(below)) >= 0) {
			return;
		}
		BigDecimal quantity = scale(stock(from).min(room(to)), 2);
		if (quantity.compareTo(new BigDecimal("100")) >= 0 && later(5, 20)) {
			transfer(from, to, quantity);
		}
	}

	private void transfer(long from, long to, BigDecimal quantity) {
		api.post(PRODUCTION, "/api/storage-movements/transfer", body("from_storage_unit_id", from,
			"to_storage_unit_id", to, "quantity", quantity, "note", "Tank aktarımı"));
	}

	/**
	 * Acilis stogu: tarif basina kucuk dolum tanklari. Akis bunlara dokunmaz (giris, aktarim, dolum yok); icerikleri
	 * sabit kalir, testler bunlari bilinen tanklar olarak kullanir.
	 */
	private void openingStock() {
		for (String[] opening : OPENING_STOCK) {
			open(opening[0], opening[1], opening[2], opening[3]);
		}
	}

	/** Acilis stogu litre verilir; tip kilo tutuluyorsa yogunlukla kiloya cevrilir. */
	private void open(String tank, String type, String litres, String acidity) {
		later(5, 10);
		long typeId = ids.type(type);
		Map<String, Object> unit = jdbc.queryForMap("select t.unit, " + DENSITY + " as density "
			+ "from production_product_types t join production_products p on p.id = t.production_product_id "
			+ "where t.id = ?", typeId);
		BigDecimal quantity = new BigDecimal(litres);
		if ("KG".equals(unit.get("unit"))) {
			quantity = scale(quantity.multiply((BigDecimal) unit.get("density")), 2);
		}
		api.post(ADMIN, "/api/storage-movements/opening", body("storage_unit_id", ids.tank(tank),
			"production_product_type_id", typeId, "quantity", quantity, "unit", unit.get("unit"),
			"attribute_values", List.of(body("production_product_attribute_definition_id", ids.acidityDefinition(typeId),
				"value_decimal", new BigDecimal(acidity))),
			"note", "Açılış stoğu"));
	}

	/** Ayin ilk gunu dolum tanklarinin asitligi olculur; olculen deger hesaplananin yerine gecer. */
	private void measureTanks() {
		for (String code : List.of("DOLUM-GEM-01", "DOLUM-AYV-01", "DOLUM-BLEND-01")) {
			long tank = ids.tank(code);
			for (Map<String, Object> material : jdbc.queryForList("""
				select m.id, m.production_product_type_id as type_id, v.value_decimal from materials m
				join material_attribute_values v on v.material_id = m.id
				join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
				join attribute_definitions a on a.id = d.attribute_definition_id and a.attribute_key = 'acidity'
				where m.status = 'active' and m.current_location_type = 'storage_unit' and m.current_location_id = ?
				order by m.id
				""", tank)) {
				if (!later(5, 15)) {
					return;
				}
				BigDecimal value = ((BigDecimal) material.get("value_decimal")).add(decimal(-0.012, 0.012))
					.setScale(2, RoundingMode.HALF_UP);
				long type = ((Number) material.get("type_id")).longValue();
				api.post(ADMIN, "/api/storage-units/" + tank + "/measurements", body("material_id", material.get("id"),
					"attributes", List.of(body("production_product_attribute_definition_id", ids.acidityDefinition(type),
						"value", value)),
					"note", "Aylık tank analizi"));
			}
		}
	}

	// ---- dolum ve depo ------------------------------------------------------------------------

	/** Raflarda yer ve tankta uygun yag oldukca gunde birkac dolum; son gun son dolumlar acik kalir. */
	private void fill(LocalDate day) {
		int runs = 0;
		List<Ids.Source> sources = new ArrayList<>(ids.sources().stream()
			.filter(source -> OPENING_STOCK.stream().noneMatch(o -> ids.tank(o[0]) == source.tankId())).toList());
		Collections.shuffle(sources, random);
		for (Ids.Source source : sources) {
			if (runs >= 5) {
				return;
			}
			Ids.Recipe recipe = fillable(source);
			if (recipe == null) {
				continue;
			}
			BigDecimal litres = stock(source.tankId()).multiply(new BigDecimal("0.97"));
			Ids.Product product = recipe.products().stream()
				.filter(p -> shelfRoom(p.volume()) >= p.minimum() && litres.divide(p.volume(), 0, RoundingMode.DOWN)
					.longValue() >= p.minimum())
				.min(Comparator.comparingLong(p -> shelfStock(p.id())))
				.orElse(null);
			if (product == null) {
				continue;
			}
			int bottles = (int) Math.min(Math.min(shelfRoom(product.volume()), product.maximum()),
				litres.divide(product.volume(), 0, RoundingMode.DOWN).longValue());
			if (!run(day, runs, source.tankId(), product, bottles)) {
				return;
			}
			runs++;
		}
	}

	/** Tanktaki karisima ve asitlige uyan tarif; yoksa null. */
	private Ids.Recipe fillable(Ids.Source source) {
		Map<Long, BigDecimal> mix = new HashMap<>();
		BigDecimal acidityTotal = BigDecimal.ZERO;
		BigDecimal total = BigDecimal.ZERO;
		for (Map<String, Object> row : jdbc.queryForList("""
			select m.production_product_type_id as type_id, v.value_decimal,
				case when m.unit = 'LITRE' then m.quantity * %s else m.quantity end as quantity
			from materials m %s
			left join material_attribute_values v on v.material_id = m.id and v.production_product_attribute_definition_id
				in (select d.id from production_product_attribute_definitions d
					join attribute_definitions a on a.id = d.attribute_definition_id and a.attribute_key = 'acidity')
			where m.status = 'active' and m.current_location_type = 'storage_unit' and m.current_location_id = ?
			""".formatted(DENSITY, MATERIAL_TYPE), source.tankId())) {
			BigDecimal quantity = (BigDecimal) row.get("quantity");
			mix.merge(((Number) row.get("type_id")).longValue(), quantity, BigDecimal::add);
			total = total.add(quantity);
			if (row.get("value_decimal") != null) {
				acidityTotal = acidityTotal.add(((BigDecimal) row.get("value_decimal")).multiply(quantity));
			}
		}
		if (total.compareTo(new BigDecimal("30")) < 0) {
			return null;
		}
		BigDecimal acidity = acidityTotal.divide(total, 4, RoundingMode.HALF_UP);
		for (Ids.Recipe recipe : source.recipes()) {
			if (recipe.matches(mix, total, acidity)) {
				return recipe;
			}
		}
		return null;
	}

	/** Dolum kosusu: plan, baslat, tamamla, rafa yerlestir. Son gun ilk kosu planli, ikincisi baslamis kalir. */
	private boolean run(LocalDate day, int index, long tank, Ids.Product product, int bottles) {
		if (!later(20, 60)) {
			return false;
		}
		ensurePackaging(product, bottles);
		long run = DemoApi.id(api.post(WAREHOUSE, "/api/finished-product-runs", body("finished_product_id", product.id(),
			"quantity", bottles, "source", body("storage_unit_id", tank, "loss_percent", new BigDecimal("0.5")))));
		boolean last = day.equals(lastDay);
		if (last && index == 0 || !later(15, 45)) {
			return true;
		}
		api.patch(WAREHOUSE, "/api/finished-product-runs/" + run + "/status", body("status", "in_progress"));
		if (last && index == 1 || !later(60, 180)) {
			return true;
		}
		api.patch(WAREHOUSE, "/api/finished-product-runs/" + run + "/status", body("status", "completed"));
		scrapAtRun(run, product);
		if (later(10, 30)) {
			stockIn(run, product, bottles);
		}
		return true;
	}

	/**
	 * Kosularin dortte birinde hatta kirilan / hatali cikan birkac ambalaj. Ayri, kosuya bagli rastgele kaynak:
	 * ana akisin sayilari kaymaz. Lotta yer yoksa yazilmaz.
	 */
	private void scrapAtRun(long run, Ids.Product product) {
		Random own = new Random(run * 104729L);
		if (own.nextDouble() >= 0.25 || product.components().isEmpty()) {
			return;
		}
		List<Long> items = new ArrayList<>(product.components().keySet());
		long item = items.get(own.nextInt(items.size()));
		int quantity = 1 + own.nextInt(4);
		Long available = jdbc.queryForObject("select coalesce(sum(quantity_remaining), 0) from packaging_lots "
			+ "where packaging_item_id = ? and status = 'active' and quantity_remaining >= ?", Long.class, item, quantity);
		if (available < quantity) {
			return;
		}
		String reason = own.nextDouble() < 0.7 ? "broken" : "defective";
		api.post(WAREHOUSE, "/api/finished-product-runs/" + run + "/packaging-scrap", body("packaging_item_id", item,
			"quantity", quantity, "reason", reason, "note", "broken".equals(reason) ? "Hatta kırıldı" : "Etiket hatalı"));
	}

	/** Bilesenlerin lotlarda yeterli adedi yoksa yeni lot gelir. */
	private void ensurePackaging(Ids.Product product, int bottles) {
		for (Map.Entry<Long, Integer> component : product.components().entrySet()) {
			long need = (long) bottles * component.getValue();
			Long available = jdbc.queryForObject("select coalesce(sum(quantity_remaining), 0) from packaging_lots "
				+ "where packaging_item_id = ? and status = 'active'", Long.class, component.getKey());
			if (available - allocated(component.getKey()) >= need) {
				continue;
			}
			receive(component.getKey(), (Math.max(need * 4, 3000) + 499) / 500 * 500);
		}
	}

	/** Ilk gun her ambalaj kaleminden bir lot. */
	private void openingPackaging() {
		for (long item : ids.packagingItems().keySet()) {
			later(2, 5);
			receive(item, 2000);
		}
	}

	/**
	 * Ambalaj lotu, kalemin turune gore tedarikcisinden (sise camciden, kapak kapakcidan, etiket matbaadan). Lot
	 * kodu tedarikcinin bas harfleri, yil ve tedarikcinin yil icindeki sira numarasi: {@code ECA-26-0012}.
	 */
	private void receive(long item, long quantity) {
		Ids.ProductSupplier supplier = ids.productSupplier(ids.packagingItems().get(item));
		long count = jdbc.queryForObject("select count(*) from packaging_lots where product_supplier_id = ? "
			+ "and extract(year from received_at) = ?", Long.class, supplier.id(), now.getYear());
		api.post(WAREHOUSE, "/api/packaging-items/" + item + "/lots", body(
			"lot_code", "%s-%02d-%04d".formatted(supplier.prefix(), now.getYear() % 100, count + 1),
			"quantity", quantity, "received_at", now.toInstant(ZoneOffset.UTC).toString(),
			"product_supplier_id", supplier.id()));
	}

	/** Planli ve devam eden kosularin ayirdigi ambalaj. */
	private long allocated(long itemId) {
		return jdbc.queryForObject("""
			select coalesce(sum(p.quantity), 0) from finished_product_run_packagings p
			join finished_product_runs r on r.id = p.finished_product_run_id and r.status in ('planned', 'in_progress')
			join packaging_lots l on l.id = p.packaging_lot_id and l.packaging_item_id = ?
			""", Long.class, itemId);
	}

	/** Siseler ayni hacimli raflara, once tercih edilen depodan. */
	private void stockIn(long run, Ids.Product product, int bottles) {
		Map<Long, List<Map<String, Object>>> byStorage = new LinkedHashMap<>();
		for (Map<String, Object> slot : jdbc.queryForList("""
			select id, finished_product_storage_id as storage_id, max_quantity - current_quantity as room
			from finished_product_storage_slots where volume_litre = ? and max_quantity > current_quantity
			and (finished_product_id is null or finished_product_id = ?)
			order by case finished_product_storage_id when ? then 0 else 1 end, finished_product_storage_id, id
			""", product.volume(), product.id(), product.storage())) {
			byStorage.computeIfAbsent(((Number) slot.get("storage_id")).longValue(), k -> new ArrayList<>()).add(slot);
		}
		long left = bottles;
		for (Map.Entry<Long, List<Map<String, Object>>> storage : byStorage.entrySet()) {
			List<Map<String, Object>> placements = new ArrayList<>();
			for (Map<String, Object> slot : storage.getValue()) {
				long take = Math.min(left, ((Number) slot.get("room")).longValue());
				if (take > 0) {
					placements.add(body("finished_product_storage_slot_id", slot.get("id"), "quantity", take));
					left -= take;
				}
			}
			if (!placements.isEmpty()) {
				api.post(WAREHOUSE, "/api/finished-product-storages/" + storage.getKey() + "/stock-in", body(
					"finished_product_id", product.id(), "finished_product_run_id", run, "placements", placements));
			}
			if (left == 0) {
				return;
			}
		}
	}

	private long shelfRoom(BigDecimal volume) {
		return jdbc.queryForObject("select coalesce(sum(max_quantity - current_quantity), 0) "
			+ "from finished_product_storage_slots where volume_litre = ?", Long.class, volume);
	}

	private long shelfStock(long productId) {
		return jdbc.queryForObject("select coalesce(sum(quantity), 0) from finished_product_storage_slot_stocks "
			+ "where finished_product_id = ?", Long.class, productId);
	}

	// ---- siparisler -------------------------------------------------------------------------------

	/** Gunde cogunlukla bir siparis: raflardaki (ayrilmamis) stoktan 1-3 kalem. */
	private void newOrders(LocalDate day) {
		double draw = random.nextDouble();
		int count = draw < 0.18 ? 0 : draw < 0.88 ? 1 : 2;
		for (int i = 0; i < count; i++) {
			if (!later(20, 120)) {
				return;
			}
			order(day);
		}
	}

	/** Son gun her durumda bir siparis kalir: taslak, onayli, stok kontrolunde, uretimde, hazir. */
	private void closingOrders(LocalDate day) {
		for (String status : List.of("draft", "confirmed", "stock_check", "in_production", "ready")) {
			Long order = draft(day);
			if (order == null) {
				return;
			}
			for (String step : List.of("confirmed", "stock_check", status)) {
				if (status.equals("draft") || !later(5, 15)) {
					break;
				}
				status(order, step.equals("ready") ? WAREHOUSE : PLANNER, step);
				if (step.equals(status)) {
					break;
				}
			}
			PendingOrder pending = pendingOrders.getLast();
			pendingOrders.remove(pending);
			if (status.equals("ready")) {
				release(pending);
			}
		}
	}

	private void order(LocalDate day) {
		Long order = draft(day);
		if (order == null || !later(5, 30)) {
			return;
		}
		PendingOrder pending = pendingOrders.getLast();
		status(order, PLANNER, "confirmed");
		pending.status = "confirmed";
		if (!later(30, 120)) {
			return;
		}
		status(order, PLANNER, "stock_check");
		pending.status = "stock_check";
	}

	/** Raflardaki serbest stoktan 1-3 kalemli taslak siparis; stok ayrilir. Raflar bossa null. */
	private Long draft(LocalDate day) {
		List<Ids.Product> available = ids.products().stream().filter(p -> free(p.id()) >= 24).toList();
		if (available.isEmpty()) {
			return null;
		}
		List<Ids.Product> chosen = new ArrayList<>(available);
		Collections.shuffle(chosen, random);
		double draw = random.nextDouble();
		chosen = chosen.subList(0, Math.min(chosen.size(), draw < 0.25 ? 1 : draw < 0.65 ? 2 : 3));
		Map<Long, Long> items = new LinkedHashMap<>();
		for (Ids.Product product : chosen) {
			long free = free(product.id());
			long quantity = Math.max(24, Math.round(free * (0.45 + random.nextDouble() * 0.5) / 6) * 6);
			items.put(product.id(), Math.min(quantity, free));
		}
		Ids.Customer customer = ids.customers().get(random.nextInt(ids.customers().size()));
		long order = DemoApi.id(api.post(PLANNER, "/api/orders", body("customer_id", customer.id(),
			"delivery_address_id", customer.addressId(), "order_date", now.toInstant(ZoneOffset.UTC).toString(),
			"items", items.entrySet().stream().map(e -> body("finished_product_id", e.getKey(), "quantity", e.getValue()))
				.toList())));
		items.forEach((product, quantity) -> reserved.merge(product, quantity, Long::sum));
		pendingOrders.add(new PendingOrder(order, items, day.plusDays(between(1, 2))));
		return order;
	}

	/** Hazirlik gunu gelen siparis raftan toplanir (ya da iptal edilir), sonra sevk edilir. */
	private void advanceOrders(LocalDate day) {
		for (PendingOrder order : List.copyOf(pendingOrders)) {
			if (order.status.equals("confirmed") && later(5, 15)) {
				status(order.id, PLANNER, "stock_check");
				order.status = "stock_check";
			}
			if (order.status.equals("stock_check") && !order.readyOn.isAfter(day)) {
				if (!later(10, 40)) {
					return;
				}
				release(order);
				if (random.nextDouble() < 0.04) {
					status(order.id, PLANNER, "cancelled");
					pendingOrders.remove(order);
					continue;
				}
				status(order.id, WAREHOUSE, "ready");
				order.status = "ready";
				order.shipOn = day.plusDays(between(0, 2));
			}
			if (order.status.equals("ready") && !order.shipOn.isAfter(day)) {
				if (!later(10, 40)) {
					return;
				}
				status(order.id, WAREHOUSE, "shipped");
				pendingOrders.remove(order);
			}
		}
	}

	/** Ayin ortasinda bir lottan kirik/hasarli ambalaj dusulur. */
	private void scrapPackaging() {
		List<Long> lots = jdbc.queryForList("select id from packaging_lots where status = 'active' "
			+ "and quantity_remaining >= 100 order by received_at, id limit 1", Long.class);
		if (!lots.isEmpty()) {
			api.post(ADMIN, "/api/packaging-lots/" + lots.getFirst() + "/adjustment", body("delta", -between(5, 25),
				"reason", "SCRAP", "note", "Kırık / hasarlı ambalaj"));
		}
	}

	private void status(long order, String user, String status) {
		api.patch(user, "/api/orders/" + order + "/status", body("status", status));
	}

	private void release(PendingOrder order) {
		order.items.forEach((product, quantity) -> reserved.merge(product, -quantity, Long::sum));
	}

	private long free(long productId) {
		return shelfStock(productId) - reserved.getOrDefault(productId, 0L);
	}

	// ---- yardimcilar -------------------------------------------------------------------------

	/**
	 * Tanktaki aktif materyallerin toplami, litre (demo tanklarinin kapasite birimi): kilo tutulan yogunlukla. Asagi
	 * yuvarlanir ki cekilen miktar tanktakini asmasin.
	 */
	private BigDecimal stock(long tank) {
		return jdbc.queryForObject("select coalesce(sum(case when m.unit = 'KG' then m.quantity / " + DENSITY
			+ " else m.quantity end), 0) from materials m " + MATERIAL_TYPE + " where m.status = 'active' "
			+ "and m.current_location_type = 'storage_unit' and m.current_location_id = ?", BigDecimal.class, tank)
			.setScale(2, RoundingMode.DOWN);
	}

	private BigDecimal room(long tank) {
		BigDecimal capacity = jdbc.queryForObject("select capacity from storage_units where id = ?", BigDecimal.class, tank);
		return capacity.subtract(stock(tank)).subtract(TANK_MARGIN).max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
	}

	private String plate() {
		String letters = "ABCDEFGHJKLMNPRSTUVYZ";
		return "%02d %c%c%c %d".formatted(between(1, 81), letters.charAt(random.nextInt(letters.length())),
			letters.charAt(random.nextInt(letters.length())), letters.charAt(random.nextInt(letters.length())),
			between(10, 999));
	}

	int between(int min, int max) {
		return min + random.nextInt(max - min + 1);
	}

	BigDecimal decimal(double min, double max) {
		return BigDecimal.valueOf(min + random.nextDouble() * (max - min));
	}

	/** Ondalik bir basamakli olcum (sicaklik gibi). */
	private BigDecimal tenth(double min, double max) {
		return scale(decimal(min, max), 1);
	}

	private static BigDecimal scale(BigDecimal value, int scale) {
		return value.setScale(scale, RoundingMode.HALF_UP);
	}

	/** Sirali, null degerli alanlari atlayan istek govdesi. */
	static Map<String, Object> body(Object... pairs) {
		Map<String, Object> body = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			if (pairs[i + 1] != null) {
				body.put((String) pairs[i], pairs[i + 1]);
			}
		}
		return body;
	}

	// ---- durum ---------------------------------------------------------------------------------

	enum Season {
		HARVEST, LATE;

		static Season of(LocalDate day) {
			Month month = day.getMonth();
			return month.compareTo(Month.MARCH) < 0 || month.compareTo(Month.OCTOBER) >= 0 ? HARVEST : LATE;
		}
	}

	enum Cultivar {
		GEMLIK("OLIVE_GEMLIK", "DINLENME-GEM-01", 0.24, 0.36, 0.72, 0.88),
		AYVALIK("OLIVE_AYVALIK", "DINLENME-AYV-01", 0.35, 0.45, 0.90, 1.00);

		final String olive;
		final String restingTank;
		private final double harvestMin;
		private final double harvestMax;
		private final double lateMin;
		private final double lateMax;

		Cultivar(String olive, String restingTank, double harvestMin, double harvestMax, double lateMin, double lateMax) {
			this.olive = olive;
			this.restingTank = restingTank;
			this.harvestMin = harvestMin;
			this.harvestMax = harvestMax;
			this.lateMin = lateMin;
			this.lateMax = lateMax;
		}

		BigDecimal acidity(Season season, DemoFactory factory) {
			return season == Season.HARVEST ? factory.decimal(harvestMin, harvestMax).setScale(2, RoundingMode.HALF_UP)
				: factory.decimal(lateMin, lateMax).setScale(2, RoundingMode.HALF_UP);
		}
	}

	/** On dinlendirme tankinda bekleyen partiler; tanka tek mevsimin yagi girer. */
	private static final class Resting {
		private Season season;
		private final List<Batch> entries = new ArrayList<>();
	}

	private static final class Batch {
		/** Teslimat; bolununce makineli asamalari yapan grubu. */
		private long id;
		private final LocalDate day;
		private final int sequence;
		private final Cultivar cultivar;
		private final List<Long> stages;
		private final BigDecimal kg;
		private BigDecimal oil;

		Batch(long id, LocalDate day, int sequence, Cultivar cultivar, List<Long> stages, BigDecimal kg) {
			this.id = id;
			this.day = day;
			this.sequence = sequence;
			this.cultivar = cultivar;
			this.stages = stages;
			this.kg = kg;
		}

		void group(long groupId) {
			id = groupId;
		}
	}

	private static final class PendingOrder {
		private final long id;
		private final Map<Long, Long> items;
		private final LocalDate readyOn;
		private LocalDate shipOn;
		private String status = "draft";

		PendingOrder(long id, Map<Long, Long> items, LocalDate readyOn) {
			this.id = id;
			this.items = items;
			this.readyOn = readyOn;
		}
	}

}
