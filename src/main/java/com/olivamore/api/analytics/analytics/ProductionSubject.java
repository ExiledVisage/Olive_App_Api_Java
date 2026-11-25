package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsQueries.BatchRow;
import com.olivamore.api.analytics.analytics.CompareQueries.Delivery;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Uretim gruplari: tedarikci, tarla, zeytin cesidi, teslimat, alim kanali, uretim hatti. Hepsi ayni partilerden
 * ({@link ProductionData}) hesaplanir; grup yalnizca partinin ve teslimatin hangi kaleme ait oldugunu soyler.
 */
final class ProductionSubject implements CompareSubject {

	private static final NumberFormat KG = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr"));

	private static final Map<String, String> CHANNELS = new LinkedHashMap<>(Map.of("farmer", "Ciftci", "trader",
		"Tuccar", "own_grove", "Kendi bahcemiz"));

	private final String key;
	private final String label;
	private final BiFunction<BatchRow, Delivery, Object> rowKey;
	private final Function<Delivery, Object> deliveryKey;
	private final AnalyticsQueries analytics;
	private final CompareQueries queries;

	private ProductionSubject(String key, String label, BiFunction<BatchRow, Delivery, Object> rowKey,
		Function<Delivery, Object> deliveryKey, AnalyticsQueries analytics, CompareQueries queries) {
		this.key = key;
		this.label = label;
		this.rowKey = rowKey;
		this.deliveryKey = deliveryKey;
		this.analytics = analytics;
		this.queries = queries;
	}

	static List<CompareSubject> all(AnalyticsQueries analytics, CompareQueries queries) {
		return List.of(
			new ProductionSubject("supplier", "Tedarikciler", (r, d) -> r.supplierId(), Delivery::supplierId,
				analytics, queries),
			new ProductionSubject("field", "Tarlalar", (r, d) -> r.fieldId(), Delivery::supplierFieldId, analytics,
				queries),
			new ProductionSubject("product_type", "Zeytin cesitleri", (r, d) -> r.productTypeId(),
				Delivery::productTypeId, analytics, queries),
			new ProductionSubject("delivery", "Teslimatlar", (r, d) -> d == null ? null : d.id(), Delivery::id,
				analytics, queries),
			new ProductionSubject("channel", "Alim kanallari", (r, d) -> d == null ? null : d.supplyChannel(),
				Delivery::supplyChannel, analytics, queries),
			new ProductionSubject("line", "Uretim hatlari", (r, d) -> d == null ? null : d.productionLineId(),
				Delivery::productionLineId, analytics, queries));
	}

	@Override
	public String key() {
		return key;
	}

	@Override
	public String label() {
		return label;
	}

	@Override
	public List<Metric> metrics() {
		return CompareMetrics.PRODUCTION;
	}

	@Override
	public boolean pricesOnly() {
		return "channel".equals(key);
	}

	@Override
	public boolean filtersProductType() {
		return true;
	}

	/** Aralikta getirisi olan kalemler, gelen kiloya gore. */
	@Override
	public List<Item> options(Context context) {
		Map<String, Double> kg = queries.deliveries(context.range(), context.productTypeId()).stream()
			.filter(d -> deliveryKey.apply(d) != null)
			.collect(Collectors.groupingBy(d -> String.valueOf(deliveryKey.apply(d)), Collectors.summingDouble(
				d -> d.quantityKg() == null ? 0 : d.quantityKg().doubleValue())));
		return items(List.copyOf(kg.keySet())).stream()
			.sorted(Comparator.comparingDouble((Item i) -> kg.get(i.id())).reversed())
			.map(i -> new Item(i.id(), i.name(), Objects.requireNonNullElse(i.detail(), "").isEmpty()
				? KG.format(kg.get(i.id())) + " kg" : i.detail() + " · " + KG.format(kg.get(i.id())) + " kg"))
			.toList();
	}

	@Override
	public List<Item> items(List<String> ids) {
		if ("channel".equals(key)) {
			return ids.stream().filter(CHANNELS::containsKey).map(id -> new Item(id, CHANNELS.get(id), null)).toList();
		}
		return queries.names(key, CompareSubject.longs(ids));
	}

	@Override
	public Values load(Context context, List<Item> items) {
		ProductionData data = ProductionData.load(analytics, queries, context.range(), context.productTypeId(),
			context.supplierId(), context.fieldId());
		return (item, when) -> data.values(
			ProductionData.rowAt(when).and(r -> item.id().equals(text(rowKey.apply(r, data.deliveryOf()
				.get(r.batchId()))))),
			ProductionData.deliveryAt(when).and(d -> item.id().equals(text(deliveryKey.apply(d)))));
	}

	private static String text(Object value) {
		return value == null ? null : value instanceof BigDecimal b ? b.toPlainString() : value.toString();
	}

}
