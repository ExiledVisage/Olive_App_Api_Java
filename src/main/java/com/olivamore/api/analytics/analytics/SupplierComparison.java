package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsQueries.BatchRow;
import com.olivamore.api.analytics.analytics.AnalyticsQueries.Delivery;
import com.olivamore.api.analytics.analytics.AnalyticsQueries.IasValue;
import com.olivamore.api.analytics.analytics.AnalyticsQueries.SupplierName;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.BatchTotals;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.olivamore.api.analytics.analytics.AnalyticsService.round;

/**
 * Tedarikci karsilastirma (docs/SUPPLIER_COMPARE.md): secilen tedarikcilerin secilen ozellikleri yan yana. Hacim ve
 * verim analizdeki parti toplamlarindan ({@link AnalyticsService#totals}), kalite IAS olcumlerinden (zeytin degerleri
 * gelen kg ile, pirina KPY'si pirina kg ile agirlikli), maliyet ve kanal alim bilgisinden (yalnizca fiyat yetkisiyle),
 * duzen teslimatlardan. Her sayisal ozellikte "yuksek iyi" ya da "dusuk iyi" tanimliysa en iyi ve en kotu isaretlenir.
 */
@Service
class SupplierComparison {

	static final String PRICE_PERMISSION = "production_batch.price";

	private static final int MIN_SUPPLIERS = 2;
	private static final int MAX_SUPPLIERS = 50;

	/** @param higherIsBetter null: yonu yok (en iyi / en kotu isaretlenmez) */
	record Metric(String key, String label, String unit, String group, Boolean higherIsBetter, boolean price) {
	}

	record Comparison(LocalDate from, LocalDate to, List<Metric> metrics, List<Supplier> suppliers, Series series) {
	}

	record Supplier(Long id, String name, String code, Map<String, Object> values, List<String> best,
		List<String> worst) {
	}

	/** Tek ozelligin aylik degerleri; {@code values} her tedarikci icin {@code periods} sirasinda (veri yoksa null). */
	record Series(String metric, List<String> periods, List<SeriesLine> lines) {
	}

	record SeriesLine(Long supplierId, List<Double> values) {
	}

	static final List<Metric> METRICS = List.of(
		new Metric("delivery_count", "Getiris sayisi", "adet", "volume", true, false),
		new Metric("incoming_kg", "Gelen zeytin", "kg", "volume", true, false),
		new Metric("pressed_kg", "Sikilan zeytin", "kg", "volume", true, false),
		new Metric("oil_kg", "Uretilen yag", "kg", "volume", true, false),
		new Metric("yield_pct", "Randiman", "%", "yield", true, false),
		new Metric("gross_yield_pct", "Brut randiman", "%", "yield", true, false),
		new Metric("pomace_ratio", "Pirina orani", "%", "yield", false, false),
		new Metric("residue_ratio", "Tortu / yag", "%", "yield", false, false),
		new Metric("avg_acidity", "Ortalama asitlik", "%", "quality", false, false),
		new Metric("olive_oil_ratio", "Zeytin yag orani (IAS)", "%", "quality", true, false),
		new Metric("olive_kpy", "Zeytin KPY", "%", "quality", true, false),
		new Metric("industrial_yield", "Beklenen randiman", "%", "quality", true, false),
		new Metric("pomace_kpy", "Pirina KPY", "%", "quality", false, false),
		new Metric("avg_price_per_kg", "Ort. kg fiyati (KDV haric)", "TL", "cost", false, true),
		new Metric("avg_price_per_kg_with_vat", "Ort. kg fiyati (KDV dahil)", "TL", "cost", false, true),
		new Metric("cost_per_kg", "Kg maliyeti (nakliye dahil)", "TL", "cost", false, true),
		new Metric("purchase_total", "Alim tutari (KDV haric)", "TL", "cost", null, true),
		new Metric("main_channel", "Kanal", null, "pattern", null, true),
		new Metric("last_delivery_at", "Son getiris", null, "pattern", null, false),
		new Metric("avg_delivery_kg", "Ort. getiris", "kg", "pattern", null, false));

	private static final Set<String> TEXT_METRICS = Set.of("main_channel", "last_delivery_at");

	private final AnalyticsQueries queries;

	SupplierComparison(AnalyticsQueries queries) {
		this.queries = queries;
	}

	/** Kullanicinin gorebildigi ozellikler (fiyat yetkisi yoksa maliyet ve kanal yok). */
	static List<Metric> visibleMetrics(boolean prices) {
		return METRICS.stream().filter(m -> prices || !m.price()).toList();
	}

	Comparison compare(String ids, String metrics, String from, String to, String productTypeId, String series,
		boolean prices) {
		Range range = Range.of(from, to);
		ValidationErrors errors = new ValidationErrors();
		List<Long> supplierIds = ids(errors, ids);
		List<Metric> visible = visibleMetrics(prices);
		Map<String, Metric> byKey = visible.stream().collect(Collectors.toMap(Metric::key, Function.identity()));
		List<Metric> selected = selected(errors, metrics, visible, byKey);
		Long type = productType(errors, productTypeId);
		if (series != null && (!byKey.containsKey(series) || TEXT_METRICS.contains(series))) {
			errors.add("series", MessageKeys.IN);
		}
		List<SupplierName> names = supplierIds.isEmpty() ? List.of() : queries.suppliers(supplierIds);
		if (!supplierIds.isEmpty() && names.size() != supplierIds.size()) {
			errors.add("ids", MessageKeys.EXISTS);
		}
		errors.throwIfAny();

		Map<Long, List<BatchRow>> rows = queries.batches(range, null, type, null).stream()
			.filter(r -> r.supplierId() != null && supplierIds.contains(r.supplierId()))
			.collect(Collectors.groupingBy(BatchRow::supplierId));
		Map<Long, List<Delivery>> deliveries = queries.deliveries(range, supplierIds, type).stream()
			.collect(Collectors.groupingBy(Delivery::supplierId));
		Map<Long, Map<String, Double>> ias = ias(rows.values().stream().flatMap(List::stream)
			.map(BatchRow::batchId).toList());

		List<Map<String, Object>> values = new ArrayList<>();
		for (SupplierName name : names) {
			values.add(values(rows.getOrDefault(name.id(), List.of()), deliveries.getOrDefault(name.id(), List.of()),
				ias));
		}
		List<Supplier> suppliers = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			SupplierName name = names.get(i);
			Map<String, Object> picked = new LinkedHashMap<>();
			for (Metric metric : selected) {
				picked.put(metric.key(), values.get(i).get(metric.key()));
			}
			suppliers.add(new Supplier(name.id(), name.name(), name.code(), picked, new ArrayList<>(),
				new ArrayList<>()));
		}
		markBestAndWorst(suppliers, selected);
		return new Comparison(range.fromDate(), range.toDate(), selected, suppliers,
			series == null ? null : series(series, range, names, rows, deliveries, ias));
	}

	// ---- degerler ------------------------------------------------------------------------------

	private static Map<String, Object> values(List<BatchRow> rows, List<Delivery> deliveries,
		Map<Long, Map<String, Double>> ias) {
		Map<String, Object> v = new HashMap<>();
		BatchTotals totals = AnalyticsService.totals(rows);
		v.put("delivery_count", deliveries.size());
		v.put("incoming_kg", totals.incomingKg());
		v.put("pressed_kg", totals.pressedKg());
		v.put("oil_kg", totals.oilKg());
		v.put("yield_pct", totals.yieldPct());
		v.put("gross_yield_pct", totals.grossYieldPct());
		v.put("pomace_ratio", totals.pressedKg() > 0 ? round(totals.pomaceKg() / totals.pressedKg() * 100, 2) : null);
		v.put("residue_ratio", totals.oilKg() > 0 ? round(totals.residueKg() / totals.oilKg() * 100, 2) : null);
		v.put("avg_acidity", totals.avgAcidity());
		v.put("olive_oil_ratio", weighted(rows, ias, "olive.oil_ratio", BatchRow::incoming));
		v.put("olive_kpy", weighted(rows, ias, "olive.kpy", BatchRow::incoming));
		v.put("industrial_yield", weighted(rows, ias, "olive.industrial_yield", BatchRow::incoming));
		v.put("pomace_kpy", weighted(rows, ias, "pomace.kpy", BatchRow::pomace));
		purchase(v, deliveries);
		v.put("last_delivery_at", deliveries.stream().map(Delivery::receivedAt).filter(Objects::nonNull)
			.max(Comparator.naturalOrder()).orElse(null));
		double deliveredKg = deliveries.stream().map(Delivery::quantityKg).filter(Objects::nonNull)
			.mapToDouble(BigDecimal::doubleValue).sum();
		v.put("avg_delivery_kg", deliveries.isEmpty() ? null : round(deliveredKg / deliveries.size(), 1));
		return v;
	}

	/** Fiyatli teslimatlar: kg agirlikli fiyat; maliyet nakliyeyle; en sik kanal. */
	private static void purchase(Map<String, Object> v, List<Delivery> deliveries) {
		double kg = 0;
		double goods = 0;
		double goodsWithVat = 0;
		double transport = 0;
		for (Delivery d : deliveries) {
			if (d.pricePerKg() == null || d.quantityKg() == null || d.quantityKg().signum() <= 0) {
				continue;
			}
			double q = d.quantityKg().doubleValue();
			double price = d.pricePerKg().doubleValue();
			double vat = d.vatRate() == null ? 0 : d.vatRate().doubleValue();
			kg += q;
			goods += q * price;
			goodsWithVat += q * price * (1 + vat / 100);
			transport += d.transportCost() == null ? 0 : d.transportCost().doubleValue();
		}
		v.put("avg_price_per_kg", kg > 0 ? round(goods / kg, 2) : null);
		v.put("avg_price_per_kg_with_vat", kg > 0 ? round(goodsWithVat / kg, 2) : null);
		v.put("cost_per_kg", kg > 0 ? round((goods + transport) / kg, 2) : null);
		v.put("purchase_total", kg > 0 ? round(goods + transport, 2) : null);
		v.put("main_channel", deliveries.stream().map(Delivery::supplyChannel).filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
			.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null));
	}

	/** Partilerin IAS degeri, partinin kilosuyla agirlikli (kilo yoksa 1). */
	private static Double weighted(List<BatchRow> rows, Map<Long, Map<String, Double>> ias, String key,
		Function<BatchRow, Double> weight) {
		double sum = 0;
		double weights = 0;
		for (BatchRow row : rows) {
			Double value = ias.getOrDefault(row.batchId(), Map.of()).get(key);
			if (value == null) {
				continue;
			}
			Double w = weight.apply(row);
			double ww = w == null || w <= 0 ? 1 : w;
			sum += value * ww;
			weights += ww;
		}
		return weights > 0 ? round(sum / weights, 2) : null;
	}

	/** parti -> "olive.kpy" gibi anahtar -> deger. */
	private Map<Long, Map<String, Double>> ias(List<Long> batchIds) {
		Map<Long, Map<String, Double>> out = new HashMap<>();
		for (IasValue value : queries.iasValues(batchIds)) {
			out.computeIfAbsent(value.batchId(), k -> new HashMap<>())
				.put(value.productKey() + "." + value.attributeKey(), value.valueDecimal().doubleValue());
		}
		return out;
	}

	// ---- en iyi / en kotu ve seri ---------------------------------------------------------------

	/** Yonu olan ve en az iki tedarikcide degeri olan ozelliklerde; esitler birlikte isaretlenir. */
	private static void markBestAndWorst(List<Supplier> suppliers, List<Metric> metrics) {
		for (Metric metric : metrics) {
			if (metric.higherIsBetter() == null) {
				continue;
			}
			List<Double> present = suppliers.stream().map(s -> number(s.values().get(metric.key())))
				.filter(Objects::nonNull).toList();
			if (present.size() < 2) {
				continue;
			}
			double max = present.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
			double min = present.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
			if (max == min) {
				continue;
			}
			double best = metric.higherIsBetter() ? max : min;
			double worst = metric.higherIsBetter() ? min : max;
			for (Supplier s : suppliers) {
				Double value = number(s.values().get(metric.key()));
				if (value != null && value == best) {
					s.best().add(metric.key());
				}
				if (value != null && value == worst) {
					s.worst().add(metric.key());
				}
			}
		}
	}

	private static Series series(String metric, Range range, List<SupplierName> names,
		Map<Long, List<BatchRow>> rows, Map<Long, List<Delivery>> deliveries, Map<Long, Map<String, Double>> ias) {
		List<YearMonth> months = new ArrayList<>();
		YearMonth last = YearMonth.from(range.toDate());
		for (YearMonth m = YearMonth.from(range.fromDate()); !m.isAfter(last); m = m.plusMonths(1)) {
			months.add(m);
		}
		List<SeriesLine> lines = new ArrayList<>();
		for (SupplierName name : names) {
			List<Double> points = new ArrayList<>();
			for (YearMonth month : months) {
				Predicate<LocalDateTime> in = at -> at != null && YearMonth.from(at).equals(month);
				List<BatchRow> monthRows = rows.getOrDefault(name.id(), List.of()).stream()
					.filter(r -> in.test(r.receivedAt())).toList();
				List<Delivery> monthDeliveries = deliveries.getOrDefault(name.id(), List.of()).stream()
					.filter(d -> in.test(d.receivedAt())).toList();
				boolean empty = monthRows.isEmpty() && monthDeliveries.isEmpty();
				points.add(empty ? null : number(values(monthRows, monthDeliveries, ias).get(metric)));
			}
			lines.add(new SeriesLine(name.id(), points));
		}
		return new Series(metric, months.stream().map(YearMonth::toString).toList(), lines);
	}

	// ---- dogrulama ------------------------------------------------------------------------------

	private static List<Long> ids(ValidationErrors errors, String raw) {
		if (raw == null || raw.isBlank()) {
			errors.add("ids", MessageKeys.REQUIRED);
			return List.of();
		}
		Set<Long> ids = new LinkedHashSet<>();
		for (String part : raw.split(",")) {
			try {
				ids.add(Long.parseLong(part.strip()));
			}
			catch (NumberFormatException e) {
				errors.add("ids", MessageKeys.INTEGER);
				return List.of();
			}
		}
		if (ids.size() < MIN_SUPPLIERS) {
			errors.add("ids", "En az iki tedarikçi seçin.");
		}
		else if (ids.size() > MAX_SUPPLIERS) {
			errors.add("ids", "En fazla " + MAX_SUPPLIERS + " tedarikçi karşılaştırılabilir.");
		}
		return List.copyOf(ids);
	}

	/** Verilmezse gorulebilen butun ozellikler, katalog sirasiyla. */
	private static List<Metric> selected(ValidationErrors errors, String raw, List<Metric> visible,
		Map<String, Metric> byKey) {
		if (raw == null || raw.isBlank()) {
			return visible;
		}
		Set<String> keys = Arrays.stream(raw.split(",")).map(String::strip).filter(k -> !k.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (!byKey.keySet().containsAll(keys)) {
			errors.add("metrics", MessageKeys.IN);
			return List.of();
		}
		return visible.stream().filter(m -> keys.contains(m.key())).toList();
	}

	private Long productType(ValidationErrors errors, String raw) {
		if (raw == null || raw.isBlank() || "0".equals(raw.strip())) {
			return null;
		}
		try {
			long id = Long.parseLong(raw.strip());
			if (!queries.exists("production_product_types", id)) {
				errors.add("product_type_id", MessageKeys.EXISTS);
				return null;
			}
			return id;
		}
		catch (NumberFormatException e) {
			errors.add("product_type_id", MessageKeys.INTEGER);
			return null;
		}
	}

	private static Double number(Object value) {
		return value instanceof Number n ? n.doubleValue() : null;
	}

}
