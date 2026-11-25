package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.CompareSubject.Context;
import com.olivamore.api.analytics.analytics.CompareSubject.Item;
import com.olivamore.api.analytics.analytics.CompareSubject.Metric;
import com.olivamore.api.analytics.analytics.CompareSubject.Values;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Karsilastirma (docs/COMPARE.md): bir gruptan secilen kalemlerin secilen ozellikleri yan yana. Grup verisini ve
 * degerlerini {@link CompareSubject} verir; burada secim dogrulanir, yonu olan ozelliklerde en iyi ve en kotu
 * isaretlenir, istenirse tek ozelligin aylik serisi kurulur. Maliyet ozellikleri ve alim kanali grubu yalnizca fiyat
 * yetkisiyle.
 */
@Service
class Comparison {

	static final String PRICE_PERMISSION = "production_batch.price";

	private static final int MIN_ITEMS = 2;
	private static final int MAX_ITEMS = 50;

	/** Grup listesi: {@code range} false ise donem secilmez (donem karsilastirmasi). */
	record Subject(String key, String label, boolean range, boolean productType) {
	}

	record Result(String subject, LocalDate from, LocalDate to, List<Metric> metrics, List<Entry> items,
		Series series) {
	}

	record Entry(String id, String name, String detail, Map<String, Object> values, List<String> best,
		List<String> worst) {
	}

	/** Tek ozelligin aylik degerleri; {@code values} her kalem icin {@code periods} sirasinda (veri yoksa null). */
	record Series(String metric, List<String> periods, List<SeriesLine> lines) {
	}

	record SeriesLine(String itemId, List<Double> values) {
	}

	/** Istek parametreleri. */
	record Query(String ids, String metrics, String from, String to, String productTypeId, String supplierId,
		String fieldId, String series) {
	}

	private final Map<String, CompareSubject> subjects = new LinkedHashMap<>();
	private final AnalyticsQueries analytics;

	Comparison(AnalyticsQueries analytics, CompareQueries queries) {
		this.analytics = analytics;
		Stream.of(ProductionSubject.all(analytics, queries), SalesSubject.all(analytics, queries),
				FacilitySubject.all(analytics, queries), List.of(new PeriodSubject(analytics, queries)))
			.flatMap(List::stream).forEach(s -> subjects.put(s.key(), s));
	}

	List<Subject> subjects(boolean prices) {
		return subjects.values().stream().filter(s -> prices || !s.pricesOnly())
			.map(s -> new Subject(s.key(), s.label(), s.usesRange(), s.filtersProductType())).toList();
	}

	List<Metric> metrics(String subject, boolean prices) {
		return visible(subject(subject, prices), prices);
	}

	List<Item> options(String key, String from, String to, String productTypeId, boolean prices) {
		CompareSubject subject = subject(key, prices);
		Range range = subject.usesRange() ? Range.of(from, to) : null;
		ValidationErrors errors = new ValidationErrors();
		Long type = id(errors, "product_type_id", productTypeId, "production_product_types");
		errors.throwIfAny();
		return subject.options(new Context(range, subject.filtersProductType() ? type : null, null, null));
	}

	Result compare(String key, Query query, boolean prices) {
		CompareSubject subject = subject(key, prices);
		Range range = subject.usesRange() ? Range.of(query.from(), query.to()) : null;
		ValidationErrors errors = new ValidationErrors();
		List<String> ids = ids(errors, query.ids());
		List<Metric> visible = visible(subject, prices);
		Map<String, Metric> byKey = visible.stream().collect(Collectors.toMap(Metric::key, Function.identity()));
		List<Metric> selected = selected(errors, query.metrics(), visible, byKey);
		Long type = id(errors, "product_type_id", query.productTypeId(), "production_product_types");
		Long supplier = id(errors, "supplier_id", query.supplierId(), "suppliers");
		Long field = id(errors, "field_id", query.fieldId(), "supplier_fields");
		String series = query.series() == null || query.series().isBlank() ? null : query.series().strip();
		if (series != null && (!subject.usesRange() || !byKey.containsKey(series) || !byKey.get(series).series())) {
			errors.add("series", MessageKeys.IN);
		}
		Map<String, Item> found = ids.isEmpty() ? Map.of() : subject.items(ids).stream()
			.collect(Collectors.toMap(Item::id, Function.identity(), (a, b) -> a));
		if (!ids.isEmpty() && !found.keySet().containsAll(ids)) {
			errors.add("ids", MessageKeys.EXISTS);
		}
		errors.throwIfAny();

		List<Item> items = ids.stream().map(found::get).toList();
		Values values = subject.load(new Context(range, subject.filtersProductType() ? type : null, supplier, field),
			items);
		List<Entry> entries = new ArrayList<>();
		for (Item item : items) {
			Map<String, Object> all = values.of(item, at -> true);
			Map<String, Object> picked = new LinkedHashMap<>();
			selected.forEach(m -> picked.put(m.key(), all.get(m.key())));
			entries.add(new Entry(item.id(), item.name(), item.detail(), picked, new ArrayList<>(), new ArrayList<>()));
		}
		markBestAndWorst(entries, selected);
		return new Result(subject.key(), range == null ? null : range.fromDate(), range == null ? null : range.toDate(),
			selected, entries, series == null ? null : series(series, range, items, values));
	}

	private CompareSubject subject(String key, boolean prices) {
		CompareSubject subject = subjects.get(key);
		if (subject == null) {
			throw new HttpStatusException(404, "Karşılaştırma grubu bulunamadı.");
		}
		if (subject.pricesOnly() && !prices) {
			throw new HttpStatusException(403, "Bu karşılaştırma için fiyat yetkisi gerekir.");
		}
		return subject;
	}

	private static List<Metric> visible(CompareSubject subject, boolean prices) {
		return subject.metrics().stream().filter(m -> prices || !m.price()).toList();
	}

	/** Yonu olan ve en az iki kalemde degeri olan ozelliklerde; esitler birlikte isaretlenir. */
	private static void markBestAndWorst(List<Entry> entries, List<Metric> metrics) {
		for (Metric metric : metrics) {
			if (metric.higherIsBetter() == null) {
				continue;
			}
			List<Double> present = entries.stream().map(e -> number(e.values().get(metric.key())))
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
			for (Entry e : entries) {
				Double value = number(e.values().get(metric.key()));
				if (value != null && value == best) {
					e.best().add(metric.key());
				}
				if (value != null && value == worst) {
					e.worst().add(metric.key());
				}
			}
		}
	}

	/** Aralik ay ay; ayda kaydi olmayan kalemin degeri null (sayilar sifir gelse de). */
	private static Series series(String metric, Range range, List<Item> items, Values values) {
		List<YearMonth> months = new ArrayList<>();
		YearMonth last = YearMonth.from(range.toDate());
		for (YearMonth m = YearMonth.from(range.fromDate()); !m.isAfter(last); m = m.plusMonths(1)) {
			months.add(m);
		}
		List<SeriesLine> lines = new ArrayList<>();
		for (Item item : items) {
			List<Double> points = new ArrayList<>();
			for (YearMonth month : months) {
				Predicate<LocalDateTime> in = at -> at != null && YearMonth.from(at).equals(month);
				Double value = number(values.of(item, in).get(metric));
				points.add(value != null && value == 0 ? null : value);
			}
			lines.add(new SeriesLine(item.id(), points));
		}
		return new Series(metric, months.stream().map(YearMonth::toString).toList(), lines);
	}

	// ---- dogrulama ------------------------------------------------------------------------------

	private static List<String> ids(ValidationErrors errors, String raw) {
		if (raw == null || raw.isBlank()) {
			errors.add("ids", MessageKeys.REQUIRED);
			return List.of();
		}
		Set<String> ids = Arrays.stream(raw.split(",")).map(String::strip).filter(s -> !s.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (ids.size() < MIN_ITEMS) {
			errors.add("ids", "Karşılaştırmak için en az iki seçim yapın.");
		}
		else if (ids.size() > MAX_ITEMS) {
			errors.add("ids", "En fazla " + MAX_ITEMS + " seçim karşılaştırılabilir.");
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

	private Long id(ValidationErrors errors, String field, String raw, String table) {
		if (raw == null || raw.isBlank() || "0".equals(raw.strip())) {
			return null;
		}
		try {
			long id = Long.parseLong(raw.strip());
			if (!analytics.exists(table, id)) {
				errors.add(field, MessageKeys.EXISTS);
				return null;
			}
			return id;
		}
		catch (NumberFormatException e) {
			errors.add(field, MessageKeys.INTEGER);
			return null;
		}
	}

	private static Double number(Object value) {
		return value instanceof Number n ? n.doubleValue() : null;
	}

}
