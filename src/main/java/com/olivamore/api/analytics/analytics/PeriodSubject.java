package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsQueries.ItemRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Donemler: ay ({@code 2026-10}), hasat sezonu ({@code 2025/26}: 1 Eylul - 31 Agustos) ya da yil ({@code 2026}).
 * Uretim ozellikleri ve satis ozeti; tedarikci ya da tarla kapsami verilirse yalnizca o uretim (satis ozeti bos).
 * Kalemler kendi araligini tasir; istek araligi ve aylik seri yok.
 */
final class PeriodSubject implements CompareSubject {

	private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");
	private static final Pattern SEASON = Pattern.compile("(\\d{4})/(\\d{2})");
	private static final Pattern YEAR = Pattern.compile("\\d{4}");

	private static final String[] MONTHS = {"Ocak", "Subat", "Mart", "Nisan", "Mayis", "Haziran", "Temmuz",
		"Agustos", "Eylul", "Ekim", "Kasim", "Aralik"};

	/** Kalemin araligi. */
	private record Period(Item item, LocalDate from, LocalDate to) {

		boolean contains(LocalDateTime at) {
			return at != null && !at.toLocalDate().isBefore(from) && !at.toLocalDate().isAfter(to);
		}

	}

	private final AnalyticsQueries analytics;
	private final CompareQueries queries;

	PeriodSubject(AnalyticsQueries analytics, CompareQueries queries) {
		this.analytics = analytics;
		this.queries = queries;
	}

	@Override
	public String key() {
		return "period";
	}

	@Override
	public String label() {
		return "Donemler";
	}

	@Override
	public List<Metric> metrics() {
		return CompareMetrics.PERIOD;
	}

	@Override
	public boolean usesRange() {
		return false;
	}

	@Override
	public boolean filtersProductType() {
		return true;
	}

	/** Son 12 ay, son 3 hasat sezonu, son 3 yil. */
	@Override
	public List<Item> options(Context context) {
		LocalDate today = LocalDate.now();
		List<String> ids = new ArrayList<>();
		YearMonth month = YearMonth.from(today);
		for (int i = 0; i < 12; i++) {
			ids.add(month.minusMonths(i).toString());
		}
		int season = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
		for (int i = 0; i < 3; i++) {
			ids.add((season - i) + "/" + String.format("%02d", (season - i + 1) % 100));
		}
		for (int i = 0; i < 3; i++) {
			ids.add(String.valueOf(today.getYear() - i));
		}
		return items(ids);
	}

	@Override
	public List<Item> items(List<String> ids) {
		return ids.stream().map(PeriodSubject::period).flatMap(Optional::stream).map(Period::item).toList();
	}

	@Override
	public Values load(Context context, List<Item> items) {
		List<Period> periods = items.stream().map(i -> period(i.id()).orElseThrow()).toList();
		LocalDate from = periods.stream().map(Period::from).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate to = periods.stream().map(Period::to).max(Comparator.naturalOrder()).orElseThrow();
		Range range = new Range(from, to);
		ProductionData production = ProductionData.load(analytics, queries, range, context.productTypeId(),
			context.supplierId(), context.fieldId());
		boolean scoped = context.supplierId() != null || context.fieldId() != null;
		List<ItemRow> sales = scoped ? List.of() : analytics.orderItems(from, to, null, null);
		return (item, when) -> {
			Period period = periods.stream().filter(p -> p.item().id().equals(item.id())).findFirst().orElseThrow();
			Predicate<LocalDateTime> in = period::contains;
			Map<String, Object> v = new HashMap<>(production.values(ProductionData.rowAt(in),
				ProductionData.deliveryAt(in)));
			if (scoped) {
				CompareMetrics.PERIOD_SALES.forEach(m -> v.put(m.key(), null));
			}
			else {
				v.putAll(SalesSubject.summary(sales.stream().filter(SalesSubject.orderedAt(in)).toList()));
			}
			return v;
		};
	}

	private static Optional<Period> period(String id) {
		try {
			if (MONTH.matcher(id).matches()) {
				YearMonth month = YearMonth.parse(id);
				return Optional.of(new Period(new Item(id, MONTHS[month.getMonthValue() - 1] + " " + month.getYear(),
					null), month.atDay(1), month.atEndOfMonth()));
			}
			var season = SEASON.matcher(id);
			if (season.matches()) {
				int start = Integer.parseInt(season.group(1));
				if ((start + 1) % 100 != Integer.parseInt(season.group(2))) {
					return Optional.empty();
				}
				return Optional.of(new Period(new Item(id, id + " hasadi", "1 Eylul - 31 Agustos"),
					LocalDate.of(start, 9, 1), LocalDate.of(start + 1, 8, 31)));
			}
			if (YEAR.matcher(id).matches()) {
				Year year = Year.parse(id);
				return Optional.of(new Period(new Item(id, id + " yili", null), year.atDay(1),
					year.atMonth(12).atEndOfMonth()));
			}
		}
		catch (DateTimeParseException e) {
			return Optional.empty();
		}
		return Optional.empty();
	}

}
