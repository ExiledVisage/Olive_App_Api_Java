package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsQueries.ItemRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.OrderTotals;
import com.olivamore.api.analytics.analytics.CompareQueries.FinishedRun;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.olivamore.api.analytics.analytics.AnalyticsService.round;

/**
 * Satis gruplari: musteriler ve bitmis urunler, iptal edilmemis siparis kalemlerinden (analizdeki musteri listesiyle
 * ayni). Bitmis urunde ayrica tamamlanan dolum.
 */
final class SalesSubject implements CompareSubject {

	private static final NumberFormat LITRE = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr"));

	private final boolean customers;
	private final AnalyticsQueries analytics;
	private final CompareQueries queries;

	private SalesSubject(boolean customers, AnalyticsQueries analytics, CompareQueries queries) {
		this.customers = customers;
		this.analytics = analytics;
		this.queries = queries;
	}

	static List<CompareSubject> all(AnalyticsQueries analytics, CompareQueries queries) {
		return List.of(new SalesSubject(true, analytics, queries), new SalesSubject(false, analytics, queries));
	}

	@Override
	public String key() {
		return customers ? "customer" : "finished_product";
	}

	@Override
	public String label() {
		return customers ? "Musteriler" : "Bitmis urunler";
	}

	@Override
	public List<Metric> metrics() {
		return customers ? CompareMetrics.CUSTOMER : CompareMetrics.FINISHED_PRODUCT;
	}

	/** Aralikta siparisi olan kalemler, litreye gore. */
	@Override
	public List<Item> options(Context context) {
		Map<String, Double> litres = analytics.orderItems(context.range().fromDate(), context.range().toDate(), null,
				null).stream()
			.filter(i -> keyOf(i) != null)
			.collect(Collectors.groupingBy(i -> keyOf(i).toString(), Collectors.summingDouble(SalesSubject::litres)));
		return items(List.copyOf(litres.keySet())).stream()
			.sorted(Comparator.comparingDouble((Item i) -> litres.get(i.id())).reversed())
			.map(i -> new Item(i.id(), i.name(), (i.detail() == null ? "" : i.detail() + " · ")
				+ LITRE.format(litres.get(i.id())) + " L"))
			.toList();
	}

	@Override
	public List<Item> items(List<String> ids) {
		return queries.names(key(), CompareSubject.longs(ids));
	}

	@Override
	public Values load(Context context, List<Item> items) {
		List<ItemRow> rows = analytics.orderItems(context.range().fromDate(), context.range().toDate(), null, null);
		List<FinishedRun> runs = customers ? List.of() : queries.finishedRuns(context.range());
		return (item, when) -> {
			Predicate<LocalDate> on = d -> when.test(d.atStartOfDay());
			List<ItemRow> mine = rows.stream()
				.filter(r -> item.id().equals(String.valueOf(keyOf(r))) && on.test(r.orderDate())).toList();
			if (customers) {
				return customerValues(mine);
			}
			Map<String, Object> v = productValues(mine);
			v.put("produced_quantity", runs.stream()
				.filter(r -> item.id().equals(String.valueOf(r.finishedProductId())) && when.test(r.completedAt()))
				.mapToLong(FinishedRun::quantity).sum());
			return v;
		};
	}

	private Long keyOf(ItemRow row) {
		return customers ? row.customerId() : row.finishedProductId();
	}

	/** Bir donemin satis ozeti (donem karsilastirmasi). */
	static Map<String, Object> summary(List<ItemRow> rows) {
		Map<String, Object> v = new HashMap<>();
		OrderTotals totals = AnalyticsService.orderTotals(rows);
		v.put("order_count", AnalyticsService.orderCount(rows));
		v.put("sold_litres", totals.litres());
		v.put("sold_quantity", totals.bottleCount());
		v.put("customer_count", rows.stream().map(ItemRow::customerId).filter(Objects::nonNull).distinct().count());
		return v;
	}

	private static Map<String, Object> customerValues(List<ItemRow> rows) {
		Map<String, Object> v = summary(rows);
		v.remove("customer_count");
		int orders = AnalyticsService.orderCount(rows);
		v.put("avg_order_litres", orders > 0 ? round(((Number) v.get("sold_litres")).doubleValue() / orders, 1) : null);
		v.put("product_variety", rows.stream().map(ItemRow::finishedProductId).filter(Objects::nonNull).distinct()
			.count());
		List<LocalDate> dates = rows.stream().map(ItemRow::orderDate).distinct().sorted().toList();
		v.put("avg_days_between_orders", dates.size() < 2 ? null
			: round((double) ChronoUnit.DAYS.between(dates.getFirst(), dates.getLast()) / (dates.size() - 1), 1));
		v.put("top_product", top(rows, ItemRow::productName, SalesSubject::litres));
		v.put("last_order_at", dates.isEmpty() ? null : dates.getLast());
		return v;
	}

	private static Map<String, Object> productValues(List<ItemRow> rows) {
		Map<String, Object> v = summary(rows);
		int orders = AnalyticsService.orderCount(rows);
		v.put("avg_quantity_per_order", orders > 0
			? round(((Number) v.get("sold_quantity")).doubleValue() / orders, 1) : null);
		v.put("top_customer", top(rows, r -> AnalyticsService.displayName(r.companyName(), r.customerName()),
			r -> (double) r.quantity()));
		v.put("last_order_at", rows.stream().map(ItemRow::orderDate).max(Comparator.naturalOrder()).orElse(null));
		return v;
	}

	private static String top(List<ItemRow> rows, Function<ItemRow, String> name, Function<ItemRow, Double> weight) {
		return rows.stream().filter(r -> name.apply(r) != null)
			.collect(Collectors.groupingBy(name, Collectors.summingDouble(weight::apply)))
			.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}

	static double litres(ItemRow row) {
		BigDecimal volume = row.volumeLitre();
		return volume == null ? 0 : volume.doubleValue() * row.quantity();
	}

	/** Siparis tarihi zaman suzgecine (gun basi). */
	static Predicate<ItemRow> orderedAt(Predicate<LocalDateTime> when) {
		return r -> r.orderDate() != null && when.test(r.orderDate().atStartOfDay());
	}

}
