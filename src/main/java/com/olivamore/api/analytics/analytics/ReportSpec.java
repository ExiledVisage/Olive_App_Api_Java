package com.olivamore.api.analytics.analytics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Rapor olusturucunun katalogu (olculer, gruplamalar, grafik turleri) ve secim kurallari. */
public final class ReportSpec {

	static final String PRODUCTION = "production";
	static final String SALES = "sales";
	static final String TIME = "time";
	static final String CATEGORY = "category";

	record Metric(String key, String label, String unit, String source, boolean additive) {
	}

	record Group(String key, String label, String kind, List<String> sources) {
	}

	record Chart(String key, String label, List<String> groupKinds, boolean additiveOnly) {
	}

	record Catalog(List<Metric> metrics, List<Group> groups, List<Chart> charts, Map<String, List<String>> filters) {
	}

	static final Map<String, Metric> METRICS = byKey(Metric::key, Stream.of(
		new Metric("incoming_kg", "Gelen zeytin", "kg", PRODUCTION, true),
		new Metric("pressed_kg", "Sikilan zeytin", "kg", PRODUCTION, true),
		new Metric("oil_kg", "Uretilen yag", "kg", PRODUCTION, true),
		new Metric("pomace_kg", "Pirina", "kg", PRODUCTION, true),
		new Metric("residue_kg", "Tortu", "kg", PRODUCTION, true),
		new Metric("batch_count", "Parti sayisi", "adet", PRODUCTION, true),
		new Metric("yield_pct", "Randiman", "%", PRODUCTION, false),
		new Metric("avg_acidity", "Ortalama asitlik", "%", PRODUCTION, false),
		new Metric("litres", "Satilan yag", "L", SALES, true),
		new Metric("bottle_count", "Satilan sise/teneke", "adet", SALES, true),
		new Metric("order_count", "Siparis sayisi", "adet", SALES, true)));

	static final Map<String, Group> GROUPS = byKey(Group::key, Stream.of(
		new Group(TIME, "Zamana gore", TIME, List.of(PRODUCTION, SALES)),
		new Group("supplier", "Tedarikciye gore", CATEGORY, List.of(PRODUCTION)),
		new Group("field", "Araziye gore", CATEGORY, List.of(PRODUCTION)),
		new Group("variety", "Zeytin cesidine gore", CATEGORY, List.of(PRODUCTION)),
		new Group("customer", "Musteriye gore", CATEGORY, List.of(SALES)),
		new Group("product", "Urune gore", CATEGORY, List.of(SALES))));

	static final Map<String, Chart> CHARTS = byKey(Chart::key, Stream.of(
		new Chart("line", "Cizgi", List.of(TIME), false),
		new Chart("column", "Sutun", List.of(TIME, CATEGORY), false),
		new Chart("bar", "Yatay cubuk", List.of(CATEGORY), false),
		new Chart("pie", "Pasta", List.of(CATEGORY), true),
		new Chart("table", "Tablo", List.of(TIME, CATEGORY), false)));

	private static final Map<String, List<String>> FILTERS = filters();

	private ReportSpec() {
	}

	private static Map<String, List<String>> filters() {
		Map<String, List<String>> filters = new LinkedHashMap<>();
		filters.put(PRODUCTION, List.of("supplier_id", "supplier_field_id", "product_type_id"));
		filters.put(SALES, List.of("customer_id", "finished_product_id"));
		return filters;
	}

	private static <T> Map<String, T> byKey(Function<T, String> key, Stream<T> items) {
		return items.collect(Collectors.toMap(key, Function.identity(), (a, b) -> a, LinkedHashMap::new));
	}

	static Catalog catalog() {
		return new Catalog(List.copyOf(METRICS.values()), List.copyOf(GROUPS.values()), List.copyOf(CHARTS.values()),
			FILTERS);
	}

	public static boolean isMetric(String key) {
		return METRICS.containsKey(key);
	}

	public static boolean isGroup(String key) {
		return GROUPS.containsKey(key);
	}

	public static boolean isChart(String key) {
		return CHARTS.containsKey(key);
	}

	/** Secimin tutarliligi; tutarsizsa neden, tutarliysa null. */
	public static String specError(String metricKey, String groupKey, String chartKey) {
		Metric metric = METRICS.get(metricKey);
		Group group = GROUPS.get(groupKey);
		if (metric == null) {
			return "Bilinmeyen olcu.";
		}
		if (group == null) {
			return "Bilinmeyen gruplama.";
		}
		if (!group.sources().contains(metric.source())) {
			return "'" + metric.label() + "' olcusu '" + group.label() + "' gruplanamaz.";
		}
		if (chartKey == null) {
			return null;
		}
		Chart chart = CHARTS.get(chartKey);
		if (chart == null) {
			return "Bilinmeyen grafik turu.";
		}
		if (!chart.groupKinds().contains(group.kind())) {
			return chart.label() + " grafigi '" + group.label() + "' icin uygun degil.";
		}
		if (chart.additiveOnly() && !metric.additive()) {
			return metric.label() + " toplanabilir bir olcu degil; pasta grafikte gosterilemez.";
		}
		return null;
	}

}
