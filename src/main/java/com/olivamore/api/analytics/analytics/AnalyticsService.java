package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsQueries.BatchRow;
import com.olivamore.api.analytics.analytics.AnalyticsQueries.CustomerNames;
import com.olivamore.api.analytics.analytics.AnalyticsQueries.ItemRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.BatchTotals;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.CustomerDetail;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.CustomerRef;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.CustomerRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.CustomerSlot;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.CustomerSummary;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Customers;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.OrderTotals;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Period;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.ProductRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Report;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.ReportRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.SupplierRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.SupplierSummary;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Suppliers;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Variety;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Yield;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.YieldSlot;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Randiman, tedarikci ve musteri ozetleri, rapor olusturucu. Toplamlar {@code double} ve satir sirasiyla toplanir,
 * oranlar yuvarlanmamis toplamlardan hesaplanir.
 */
@Service
@Transactional(readOnly = true)
class AnalyticsService {

	private static final String DAY = "day";
	private static final String WEEK = "week";
	private static final String MONTH = "month";

	private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

	private record Bucket(String key, String start) {
	}

	private final AnalyticsQueries queries;

	AnalyticsService(AnalyticsQueries queries) {
		this.queries = queries;
	}

	// ---- randiman ------------------------------------------------------------------

	Yield yield(String from, String to, String productTypeId, String supplierId) {
		Range range = Range.of(from, to);
		ValidationErrors errors = new ValidationErrors();
		Long type = existingId(errors, "product_type_id", productTypeId, "production_product_types");
		Long supplier = existingId(errors, "supplier_id", supplierId, "suppliers");
		errors.throwIfAny();

		List<BatchRow> rows = queries.batches(range, supplier, type, null);
		String bucket = bucketFor(range);
		List<YieldSlot> series = emptyBuckets(range, bucket).stream()
			.map(slot -> new YieldSlot(slot.key(), slot.start(), totals(rows.stream()
				.filter(r -> bucketKey(r.receivedAt().toLocalDate(), bucket).equals(slot.key())).toList())))
			.toList();
		List<Variety> varieties = new ArrayList<>(groupBy(rows, BatchRow::productTypeId).values().stream()
			.map(group -> new Variety(group.getFirst().productTypeId(), group.getFirst().productTypeName(), totals(group)))
			.toList());
		varieties.sort(descending(v -> v.totals().pressedKg()));
		return new Yield(bucket, totals(rows), series, varieties);
	}

	// ---- tedarikciler ----------------------------------------------------------------

	Suppliers suppliers(String from, String to, String productTypeId) {
		Range range = Range.of(from, to);
		ValidationErrors errors = new ValidationErrors();
		Long type = existingId(errors, "product_type_id", productTypeId, "production_product_types");
		errors.throwIfAny();

		List<BatchRow> rows = queries.batches(range, null, type, null).stream()
			.filter(r -> r.supplierId() != null).toList();
		List<SupplierRow> suppliers = new ArrayList<>(groupBy(rows, BatchRow::supplierId).values().stream()
			.map(group -> {
				BatchRow first = group.getFirst();
				return new SupplierRow(first.supplierId(), first.supplierName(), first.supplierCode(),
					group.stream().map(BatchRow::receivedAt).filter(Objects::nonNull)
						.max(Comparator.naturalOrder()).orElse(null),
					totals(group));
			})
			.toList());
		suppliers.sort(descending(s -> s.totals().incomingKg()));
		return new Suppliers(new SupplierSummary(totals(rows), suppliers.size()), suppliers);
	}

	// ---- musteriler -------------------------------------------------------------------

	Customers customers(String from, String to) {
		Range range = Range.of(from, to);
		List<ItemRow> items = queries.orderItems(range.fromDate(), range.toDate(), null, null);
		List<CustomerRow> customers = new ArrayList<>(groupBy(items, ItemRow::customerId).values().stream()
			.map(group -> {
				ItemRow first = group.getFirst();
				List<ProductRow> products = productBreakdown(group);
				return new CustomerRow(first.customerId(), displayName(first.companyName(), first.customerName()),
					orderCount(group), orderTotals(group), products.isEmpty() ? null : products.getFirst().name());
			})
			.toList());
		customers.sort(descending(c -> c.totals().litres()));
		return new Customers(new CustomerSummary(orderTotals(items), orderCount(items), customers.size()), customers);
	}

	/** Tek musteri: donemlere gore siparisler, urun tercihi, bir gun uzun onceki donem. */
	CustomerDetail customer(long id, String from, String to) {
		CustomerNames customer = queries.customer(id).orElseThrow(() -> new ModelNotFoundException("Customer", id));
		Range range = Range.of(from, to);
		List<ItemRow> items = queries.orderItems(range.fromDate(), range.toDate(), id, null);
		String bucket = bucketFor(range);
		List<CustomerSlot> series = emptyBuckets(range, bucket).stream()
			.map(slot -> {
				List<ItemRow> inSlot = items.stream()
					.filter(i -> i.orderDate() != null && bucketKey(i.orderDate(), bucket).equals(slot.key())).toList();
				return new CustomerSlot(slot.key(), slot.start(), orderTotals(inSlot), orderCount(inSlot));
			})
			.toList();
		long days = range.inclusiveDays();
		List<ItemRow> previous = queries.orderItems(range.fromDate().minusDays(days + 1), range.fromDate().minusDays(1),
			id, null);
		return new CustomerDetail(new CustomerRef(customer.id(), displayName(customer.companyName(), customer.name())),
			bucket, period(items), period(previous), series, productBreakdown(items));
	}

	private static Period period(List<ItemRow> items) {
		return new Period(orderTotals(items), orderCount(items));
	}

	// ---- rapor olusturucu --------------------------------------------------------------

	Report report(ReportQuery query) {
		Range range = Range.of(query.from(), query.to());
		ValidationErrors errors = new ValidationErrors();
		choice(errors, "metric", query.metric(), true, ReportSpec::isMetric);
		choice(errors, "group", query.group(), true, ReportSpec::isGroup);
		choice(errors, "chart", query.chart(), false, ReportSpec::isChart);
		Long supplierId = integer(errors, "supplier_id", query.supplierId());
		Long fieldId = integer(errors, "supplier_field_id", query.supplierFieldId());
		Long productTypeId = integer(errors, "product_type_id", query.productTypeId());
		Long customerId = integer(errors, "customer_id", query.customerId());
		Long finishedProductId = integer(errors, "finished_product_id", query.finishedProductId());
		errors.throwIfAny();
		String error = ReportSpec.specError(query.metric(), query.group(), query.chart());
		if (error != null) {
			throw new HttpStatusException(422, error);
		}

		String metric = query.metric();
		String group = query.group();
		boolean production = ReportSpec.METRICS.get(metric).source().equals(ReportSpec.PRODUCTION);
		List<BatchRow> batches = production
			? queries.batches(range, positive(supplierId), positive(productTypeId), positive(fieldId)) : List.of();
		List<ItemRow> items = production ? List.of()
			: queries.orderItems(range.fromDate(), range.toDate(), positive(customerId), positive(finishedProductId));

		boolean byTime = ReportSpec.GROUPS.get(group).kind().equals(ReportSpec.TIME);
		String bucket = byTime ? bucketFor(range) : null;
		List<ReportRow> rows;
		if (byTime) {
			rows = timeRows(range, bucket, metric, production, batches, items);
		}
		else {
			rows = new ArrayList<>(production ? batchRows(group, metric, batches) : itemRows(group, metric, items));
			rows.sort(descending(r -> r.value() == null ? Double.NEGATIVE_INFINITY : r.value()));
		}
		Double total = production ? productionValue(metric, batches) : salesValue(metric, items);
		return new Report(ReportSpec.METRICS.get(metric), ReportSpec.GROUPS.get(group), bucket, rows,
			production ? "parti" : "siparis", total);
	}

	private static List<ReportRow> timeRows(Range range, String bucket, String metric, boolean production,
		List<BatchRow> batches, List<ItemRow> items) {
		return emptyBuckets(range, bucket).stream().map(slot -> {
			if (production) {
				List<BatchRow> subset = batches.stream()
					.filter(r -> bucketKey(r.receivedAt().toLocalDate(), bucket).equals(slot.key())).toList();
				return new ReportRow(slot.key(), slot.start(), productionValue(metric, subset), subset.size());
			}
			List<ItemRow> subset = items.stream()
				.filter(i -> i.orderDate() != null && bucketKey(i.orderDate(), bucket).equals(slot.key())).toList();
			return new ReportRow(slot.key(), slot.start(), salesValue(metric, subset), orderCount(subset));
		}).toList();
	}

	private static List<ReportRow> batchRows(String group, String metric, List<BatchRow> batches) {
		Function<BatchRow, Long> keyOf = switch (group) {
			case "supplier" -> BatchRow::supplierId;
			case "field" -> BatchRow::fieldId;
			default -> BatchRow::productTypeId;
		};
		Function<BatchRow, String> labelOf = switch (group) {
			case "supplier" -> r -> (text(r.supplierName()) + " (" + text(r.supplierCode()) + ")").strip();
			case "field" -> r -> text(r.fieldCode()) + " • " + text(r.supplierName());
			default -> BatchRow::productTypeName;
		};
		List<BatchRow> keyed = batches.stream().filter(r -> keyOf.apply(r) != null).toList();
		return groupBy(keyed, keyOf).entrySet().stream()
			.map(e -> new ReportRow(String.valueOf(e.getKey()), labelOf.apply(e.getValue().getFirst()),
				productionValue(metric, e.getValue()), e.getValue().size()))
			.toList();
	}

	private static List<ReportRow> itemRows(String group, String metric, List<ItemRow> items) {
		boolean byCustomer = group.equals("customer");
		Function<ItemRow, Long> keyOf = byCustomer ? ItemRow::customerId : ItemRow::finishedProductId;
		Function<ItemRow, String> labelOf = byCustomer
			? i -> displayName(i.companyName(), i.customerName()) : ItemRow::productName;
		List<ItemRow> keyed = items.stream().filter(i -> keyOf.apply(i) != null).toList();
		return groupBy(keyed, keyOf).entrySet().stream()
			.map(e -> new ReportRow(String.valueOf(e.getKey()), labelOf.apply(e.getValue().getFirst()),
				salesValue(metric, e.getValue()), orderCount(e.getValue())))
			.toList();
	}

	private static Double productionValue(String metric, List<BatchRow> rows) {
		BatchTotals totals = totals(rows);
		return switch (metric) {
			case "incoming_kg" -> totals.incomingKg();
			case "pressed_kg" -> totals.pressedKg();
			case "oil_kg" -> totals.oilKg();
			case "pomace_kg" -> totals.pomaceKg();
			case "batch_count" -> (double) totals.batchCount();
			case "yield_pct" -> totals.yieldPct();
			default -> totals.avgAcidity();
		};
	}

	private static Double salesValue(String metric, List<ItemRow> items) {
		OrderTotals totals = orderTotals(items);
		return switch (metric) {
			case "litres" -> totals.litres();
			case "bottle_count" -> (double) totals.bottleCount();
			default -> (double) orderCount(items);
		};
	}

	// ---- toplamlar -----------------------------------------------------------------------

	private static BatchTotals totals(List<BatchRow> rows) {
		double incoming = 0;
		double pressed = 0;
		double oil = 0;
		double pomace = 0;
		double aciditySum = 0;
		int acidityCount = 0;
		for (BatchRow r : rows) {
			incoming += r.incoming() == null ? 0 : r.incoming();
			pressed += r.pressed() == null ? 0 : r.pressed();
			oil += r.oil() == null ? 0 : r.oil();
			pomace += r.pomace() == null ? 0 : r.pomace();
			if (r.acidity() != null) {
				aciditySum += r.acidity();
				acidityCount++;
			}
		}
		return new BatchTotals(rows.size(), round(incoming, 1), round(pressed, 1), round(oil, 1), round(pomace, 1),
			pressed > 0 ? round(oil / pressed * 100, 2) : null,
			incoming > 0 ? round(oil / incoming * 100, 2) : null,
			acidityCount == 0 ? null : round(aciditySum / acidityCount, 2));
	}

	private static OrderTotals orderTotals(List<ItemRow> items) {
		long bottles = 0;
		double litres = 0;
		for (ItemRow item : items) {
			bottles += item.quantity();
			litres += item.quantity() * (item.volumeLitre() == null ? 0 : item.volumeLitre().doubleValue());
		}
		return new OrderTotals(bottles, round(litres, 1));
	}

	private static List<ProductRow> productBreakdown(List<ItemRow> items) {
		List<ProductRow> products = new ArrayList<>(groupBy(items, ItemRow::finishedProductId).values().stream()
			.map(group -> new ProductRow(group.getFirst().finishedProductId(), group.getFirst().productName(),
				orderTotals(group)))
			.toList());
		products.sort(descending(p -> p.totals().litres()));
		return products;
	}

	private static int orderCount(List<ItemRow> items) {
		return new LinkedHashSet<>(items.stream().map(ItemRow::orderId).toList()).size();
	}

	/** Firma adi doluysa o, degilse kisi adi. */
	private static String displayName(String company, String name) {
		return company != null && !company.isEmpty() && !"0".equals(company) ? company : name;
	}

	/** Yarim deger sifirdan uzaga; en kisa ondalik gosterim uzerinden ({@code 0.285 -> 0.29}). */
	private static double round(double value, int places) {
		if (!Double.isFinite(value) || value == 0) {
			return value;
		}
		return new BigDecimal(Double.toString(value)).setScale(places, RoundingMode.HALF_UP).doubleValue();
	}

	// ---- zaman dilimleri ---------------------------------------------------------------------

	/** 30 gune kadar gun, 119 gune kadar hafta, ustu ay. */
	private static String bucketFor(Range range) {
		long days = range.inclusiveDays();
		if (days <= 30) {
			return DAY;
		}
		return days <= 119 ? WEEK : MONTH;
	}

	/** Gun: Y-m-d; hafta: pazartesi; ay: Y-m. */
	private static String bucketKey(LocalDate date, String bucket) {
		return switch (bucket) {
			case DAY -> date.toString();
			case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
			default -> date.format(YEAR_MONTH);
		};
	}

	/** Araligin butun dilimleri, bos olanlar dahil. */
	private static List<Bucket> emptyBuckets(Range range, String bucket) {
		List<Bucket> slots = new ArrayList<>();
		LocalDate cursor = switch (bucket) {
			case DAY -> range.fromDate();
			case WEEK -> range.fromDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			default -> range.fromDate().withDayOfMonth(1);
		};
		while (!cursor.isAfter(range.toDate())) {
			slots.add(new Bucket(bucketKey(cursor, bucket), cursor.toString()));
			cursor = switch (bucket) {
				case DAY -> cursor.plusDays(1);
				case WEEK -> cursor.plusWeeks(1);
				default -> cursor.plusMonths(1);
			};
		}
		return slots;
	}

	// ---- dogrulama ve yardimcilar ---------------------------------------------------------------

	private Long existingId(ValidationErrors errors, String field, String raw, String table) {
		Long id = integer(errors, field, raw);
		if (id == null) {
			return null;
		}
		if (!queries.exists(table, id)) {
			errors.add(field, MessageKeys.EXISTS);
			return null;
		}
		return positive(id);
	}

	private static Long integer(ValidationErrors errors, String field, String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return Long.parseLong(raw.strip());
		}
		catch (NumberFormatException e) {
			errors.add(field, MessageKeys.INTEGER);
			return null;
		}
	}

	private static void choice(ValidationErrors errors, String field, String value, boolean required,
		Predicate<String> allowed) {
		if (value == null) {
			if (required) {
				errors.add(field, MessageKeys.REQUIRED);
			}
		}
		else if (!allowed.test(value)) {
			errors.add(field, MessageKeys.IN);
		}
	}

	/** Sifir filtre verilmemis sayilir. */
	private static Long positive(Long id) {
		return id == null || id == 0 ? null : id;
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	/** Ilk gorulme sirasiyla; anahtar {@code null} olabilir. */
	private static <T, K> Map<K, List<T>> groupBy(List<T> items, Function<T, K> key) {
		Map<K, List<T>> groups = new LinkedHashMap<>();
		for (T item : items) {
			groups.computeIfAbsent(key.apply(item), k -> new ArrayList<>()).add(item);
		}
		return groups;
	}

	/** Kararli, buyukten kucuge. */
	private static <T> Comparator<T> descending(ToDoubleFunction<T> value) {
		return Comparator.comparingDouble(value).reversed();
	}

}
