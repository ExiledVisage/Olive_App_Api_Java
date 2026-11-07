package com.olivamore.api.analytics;

import com.olivamore.api.analytics.Analytics.BatchRow;
import com.olivamore.api.analytics.Analytics.ItemRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Laravel {@code AnalyticsService}'in rapor olusturucusu: olcu x gruplama x filtre x donem. Olculer, gruplamalar,
 * grafik turleri ve kurallari Laravel'deki sabitlerin aynisi (sirasi da).
 */
final class ReportSpec {

	static final Map<String, Map<String, Object>> METRICS = new LinkedHashMap<>();
	static final Map<String, Map<String, Object>> GROUPS = new LinkedHashMap<>();
	static final Map<String, Map<String, Object>> CHARTS = new LinkedHashMap<>();
	static final Map<String, List<String>> FILTERS = new LinkedHashMap<>();

	static {
		metric("incoming_kg", "Gelen zeytin", "kg", "production", true);
		metric("pressed_kg", "Sikilan zeytin", "kg", "production", true);
		metric("oil_kg", "Uretilen yag", "kg", "production", true);
		metric("pomace_kg", "Pirina", "kg", "production", true);
		metric("batch_count", "Parti sayisi", "adet", "production", true);
		metric("yield_pct", "Randiman", "%", "production", false);
		metric("avg_acidity", "Ortalama asitlik", "%", "production", false);
		metric("litres", "Satilan yag", "L", "sales", true);
		metric("bottle_count", "Satilan sise/teneke", "adet", "sales", true);
		metric("order_count", "Siparis sayisi", "adet", "sales", true);

		group("time", "Zamana gore", "time", List.of("production", "sales"));
		group("supplier", "Tedarikciye gore", "category", List.of("production"));
		group("field", "Araziye gore", "category", List.of("production"));
		group("variety", "Zeytin cesidine gore", "category", List.of("production"));
		group("customer", "Musteriye gore", "category", List.of("sales"));
		group("product", "Urune gore", "category", List.of("sales"));

		chart("line", "Cizgi", List.of("time"), false);
		chart("column", "Sutun", List.of("time", "category"), false);
		chart("bar", "Yatay cubuk", List.of("category"), false);
		chart("pie", "Pasta", List.of("category"), true);
		chart("table", "Tablo", List.of("time", "category"), false);

		FILTERS.put("production", List.of("supplier_id", "supplier_field_id", "product_type_id"));
		FILTERS.put("sales", List.of("customer_id", "finished_product_id"));
	}

	private ReportSpec() {
	}

	private static void metric(String key, String label, String unit, String source, boolean additive) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("label", label);
		m.put("unit", unit);
		m.put("source", source);
		m.put("additive", additive);
		METRICS.put(key, m);
	}

	private static void group(String key, String label, String kind, List<String> sources) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("label", label);
		m.put("kind", kind);
		m.put("sources", sources);
		GROUPS.put(key, m);
	}

	private static void chart(String key, String label, List<String> groupKinds, boolean additiveOnly) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("label", label);
		m.put("group_kinds", groupKinds);
		m.put("additive_only", additiveOnly);
		CHARTS.put(key, m);
	}

	static Map<String, Object> catalog() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("metrics", list(METRICS));
		out.put("groups", list(GROUPS));
		out.put("charts", list(CHARTS));
		out.put("filters", FILTERS);
		return out;
	}

	private static List<Map<String, Object>> list(Map<String, Map<String, Object>> items) {
		List<Map<String, Object>> out = new ArrayList<>();
		items.forEach((key, item) -> out.add(withKey(key, item)));
		return out;
	}

	private static Map<String, Object> withKey(String key, Map<String, Object> item) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("key", key);
		m.putAll(item);
		return m;
	}

	/** Secimin tutarliligi; tutarsizsa neden, tutarliysa null. */
	@SuppressWarnings("unchecked")
	static String specError(String metric, String group, String chart) {
		Map<String, Object> m = METRICS.get(metric);
		Map<String, Object> g = GROUPS.get(group);
		if (m == null) {
			return "Bilinmeyen olcu.";
		}
		if (g == null) {
			return "Bilinmeyen gruplama.";
		}
		if (!((List<String>) g.get("sources")).contains((String) m.get("source"))) {
			return "'" + m.get("label") + "' olcusu '" + g.get("label") + "' gruplanamaz.";
		}
		if (chart == null) {
			return null;
		}
		Map<String, Object> c = CHARTS.get(chart);
		if (c == null) {
			return "Bilinmeyen grafik turu.";
		}
		if (!((List<String>) c.get("group_kinds")).contains((String) g.get("kind"))) {
			return c.get("label") + " grafigi '" + g.get("label") + "' icin uygun degil.";
		}
		if (Boolean.TRUE.equals(c.get("additive_only")) && !Boolean.TRUE.equals(m.get("additive"))) {
			return m.get("label") + " toplanabilir bir olcu degil; pasta grafikte gosterilemez.";
		}
		return null;
	}

	/** Olcu degeri, zaman diliminde ya da kategoride; toplam tum satirlarin birlikte degeri. */
	static Map<String, Object> query(Analytics analytics, String metric, String group, Analytics.Range range, Map<String, Object> filters) {
		Map<String, Object> m = METRICS.get(metric);
		Map<String, Object> g = GROUPS.get(group);
		String source = (String) m.get("source");
		Map<String, Object> applied = new LinkedHashMap<>();
		filters.forEach((k, v) -> {
			if (FILTERS.get(source).contains(k)) {
				applied.put(k, v);
			}
		});
		boolean production = source.equals("production");
		List<BatchRow> batches = production
			? analytics.batchRows(range, applied.get("supplier_id"), applied.get("product_type_id"), applied.get("supplier_field_id"))
			: List.of();
		List<ItemRow> items = production ? List.of()
			: analytics.orderItems(range.fromDate(), range.toDate(), applied.get("customer_id"), applied.get("finished_product_id"));

		String bucket = null;
		List<Map<String, Object>> rows = new ArrayList<>();
		if ("time".equals(g.get("kind"))) {
			bucket = Analytics.bucketFor(range);
			for (Map<String, Object> slot : Analytics.emptyBuckets(range, bucket)) {
				String b = bucket;
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("key", slot.get("key"));
				row.put("label", slot.get("start"));
				if (production) {
					List<BatchRow> subset = batches.stream().filter(r -> Analytics.bucketKey(r.receivedAt().toLocalDate(), b).equals(slot.get("key"))).toList();
					row.put("value", productionValue(metric, subset));
					row.put("count", subset.size());
				}
				else {
					List<ItemRow> subset = items.stream().filter(i -> i.orderDate() != null
						&& Analytics.bucketKey(i.orderDate(), b).equals(slot.get("key"))).toList();
					row.put("value", salesValue(metric, subset));
					row.put("count", Analytics.orderCount(subset));
				}
				rows.add(row);
			}
		}
		else if (production) {
			Function<BatchRow, Object> keyOf = switch (group) {
				case "supplier" -> BatchRow::supplierId;
				case "field" -> BatchRow::fieldId;
				default -> BatchRow::productTypeId;
			};
			Function<BatchRow, String> labelOf = switch (group) {
				case "supplier" -> r -> (str(r.supplierName()) + " (" + str(r.supplierCode()) + ")").strip();
				case "field" -> r -> str(r.fieldCode()) + " • " + str(r.supplierName());
				default -> BatchRow::productTypeName;
			};
			Analytics.groupBy(batches.stream().filter(r -> keyOf.apply(r) != null).toList(), keyOf).forEach((key, subset) -> {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("key", String.valueOf(key));
				row.put("label", labelOf.apply(subset.getFirst()));
				row.put("value", productionValue(metric, subset));
				row.put("count", subset.size());
				rows.add(row);
			});
			Analytics.sortByDesc(rows, "value");
		}
		else {
			Function<ItemRow, Object> keyOf = group.equals("customer") ? ItemRow::customerId : ItemRow::finishedProductId;
			Function<ItemRow, Object> labelOf = group.equals("customer") ? i -> Analytics.customerName(i.customer())
				: i -> i.product() == null ? null : i.product().get("name");
			Analytics.groupBy(items.stream().filter(i -> keyOf.apply(i) != null).toList(), keyOf).forEach((key, subset) -> {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("key", String.valueOf(key));
				row.put("label", labelOf.apply(subset.getFirst()));
				row.put("value", salesValue(metric, subset));
				row.put("count", Analytics.orderCount(subset));
				rows.add(row);
			});
			Analytics.sortByDesc(rows, "value");
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("metric", withKey(metric, m));
		out.put("group", withKey(group, g));
		out.put("bucket", bucket);
		out.put("rows", rows);
		out.put("count_unit", production ? "parti" : "siparis");
		out.put("total", production ? productionValue(metric, batches) : salesValue(metric, items));
		return out;
	}

	private static Double productionValue(String metric, List<BatchRow> subset) {
		Object value = Analytics.totals(subset).get(metric);
		return value == null ? null : ((Number) value).doubleValue();
	}

	private static Double salesValue(String metric, List<ItemRow> subset) {
		Map<String, Object> totals = new LinkedHashMap<>(Analytics.orderTotals(subset));
		totals.put("order_count", Analytics.orderCount(subset));
		Object value = totals.get(metric);
		return value == null ? null : ((Number) value).doubleValue();
	}

	private static String str(String value) {
		return value == null ? "" : value;
	}

}
