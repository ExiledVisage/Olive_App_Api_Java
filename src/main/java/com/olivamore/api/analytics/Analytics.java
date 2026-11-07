package com.olivamore.api.analytics;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Laravel {@code AnalyticsService}: randiman, tedarikci ve musteri ozetleri, rapor olusturucu.
 * <p>
 * Asamalar adlarindan degil gorevlerinden taninir: gelen zeytin (zeytin tartan ilk tartim), sikilan zeytin
 * (donusumun {@code input_quantity}'si), yag ve pirina (tartim), asitlik (yag analizi). Toplamlar uzerinden
 * hesaplanir; PHP float (double) toplama sirasi korunur.
 */
@Component
class Analytics {

	static final String DAY = "day";
	static final String WEEK = "week";
	static final String MONTH = "month";

	/** Parti satiri: gelen, sikilan, yag, pirina, asitlik. */
	record BatchRow(long batchId, LocalDateTime receivedAt, Long supplierId, String supplierName, String supplierCode, Long fieldId,
		String fieldCode, Long productTypeId, String productTypeName, Double incoming, Double pressed, Double oil, Double pomace,
		Double acidity) {
	}

	/** Iptal edilmemis siparisin kalemi. */
	record ItemRow(long orderId, Long customerId, Row customer, LocalDate orderDate, Long finishedProductId, Row product, long quantity) {
	}

	private final Db db;

	Analytics(Db db) {
		this.db = db;
	}

	// ---- randiman ------------------------------------------------------------------

	Map<String, Object> yield(Range range, Long productTypeId, Long supplierId) {
		List<BatchRow> rows = batchRows(range, supplierId, productTypeId, null);
		String bucket = bucketFor(range);
		List<Map<String, Object>> series = new ArrayList<>();
		for (Map<String, Object> slot : emptyBuckets(range, bucket)) {
			Map<String, Object> m = new LinkedHashMap<>(slot);
			m.putAll(totals(rows.stream().filter(r -> bucketKey(r.receivedAt().toLocalDate(), bucket).equals(slot.get("key"))).toList()));
			series.add(m);
		}
		List<Map<String, Object>> byVariety = new ArrayList<>();
		for (List<BatchRow> group : groupBy(rows, r -> r.productTypeId() == null ? "" : r.productTypeId()).values()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("product_type_id", group.getFirst().productTypeId());
			m.put("name", group.getFirst().productTypeName());
			m.putAll(totals(group));
			byVariety.add(m);
		}
		sortByDesc(byVariety, "pressed_kg");
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("bucket", bucket);
		out.put("summary", totals(rows));
		out.put("series", series);
		out.put("by_variety", byVariety);
		return out;
	}

	// ---- tedarikciler ----------------------------------------------------------------

	Map<String, Object> suppliers(Range range, Long productTypeId) {
		List<BatchRow> rows = batchRows(range, null, productTypeId, null).stream().filter(r -> r.supplierId() != null).toList();
		List<Map<String, Object>> suppliers = new ArrayList<>();
		for (List<BatchRow> group : groupBy(rows, BatchRow::supplierId).values()) {
			BatchRow first = group.getFirst();
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("supplier_id", first.supplierId());
			m.put("name", first.supplierName());
			m.put("code", first.supplierCode());
			m.put("last_delivery_at", group.stream().map(BatchRow::receivedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
			m.putAll(totals(group));
			suppliers.add(m);
		}
		sortByDesc(suppliers, "incoming_kg");
		Map<String, Object> summary = new LinkedHashMap<>(totals(rows));
		summary.put("supplier_count", suppliers.size());
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("summary", summary);
		out.put("suppliers", suppliers);
		return out;
	}

	// ---- musteriler -------------------------------------------------------------------

	Map<String, Object> customers(Range range) {
		List<ItemRow> items = orderItems(range.fromDate(), range.toDate(), null, null);
		List<Map<String, Object>> customers = new ArrayList<>();
		for (List<ItemRow> group : groupBy(items, i -> i.customerId() == null ? "" : i.customerId()).values()) {
			Row customer = group.getFirst().customer();
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("customer_id", customer == null ? null : customer.get("id"));
			m.put("name", customerName(customer));
			m.put("order_count", orderCount(group));
			m.putAll(orderTotals(group));
			List<Map<String, Object>> products = productBreakdown(group);
			m.put("top_product", products.isEmpty() ? null : products.getFirst().get("name"));
			customers.add(m);
		}
		sortByDesc(customers, "litres");
		Map<String, Object> summary = new LinkedHashMap<>(orderTotals(items));
		summary.put("order_count", orderCount(items));
		summary.put("customer_count", customers.size());
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("summary", summary);
		out.put("customers", customers);
		return out;
	}

	/**
	 * Tek musteri: donemlere gore siparisler, urun tercihi, onceki donem. Onceki donem Laravel'deki gibi
	 * {@code subDays(gun + 0.99..)}: bir gun uzun (Carbon ondalik gunu yuvarliyor).
	 */
	Map<String, Object> customer(Row customer, Range range) {
		List<ItemRow> items = orderItems(range.fromDate(), range.toDate(), customer.id(), null);
		String bucket = bucketFor(range);
		List<Map<String, Object>> series = new ArrayList<>();
		for (Map<String, Object> slot : emptyBuckets(range, bucket)) {
			List<ItemRow> inSlot = items.stream().filter(i -> i.orderDate() != null && bucketKey(i.orderDate(), bucket).equals(slot.get("key"))).toList();
			Map<String, Object> m = new LinkedHashMap<>(slot);
			m.putAll(orderTotals(inSlot));
			m.put("order_count", orderCount(inSlot));
			series.add(m);
		}
		long days = range.inclusiveDays();
		List<ItemRow> previous = orderItems(range.fromDate().minusDays(days + 1), range.fromDate().minusDays(1), customer.id(), null);
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("id", customer.get("id"));
		c.put("name", customerName(customer));
		Map<String, Object> summary = new LinkedHashMap<>(orderTotals(items));
		summary.put("order_count", orderCount(items));
		Map<String, Object> prev = new LinkedHashMap<>(orderTotals(previous));
		prev.put("order_count", orderCount(previous));
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("customer", c);
		out.put("bucket", bucket);
		out.put("summary", summary);
		out.put("previous", prev);
		out.put("series", series);
		out.put("products", productBreakdown(items));
		return out;
	}

	// ---- parti satirlari ------------------------------------------------------------------

	/** Aralikta gelmis, yagi tartilmis ve sikilan miktari olan partiler. */
	List<BatchRow> batchRows(Range range, Object supplierId, Object productTypeId, Object fieldId) {
		Map<Long, Role> roles = stageRoles();
		StringBuilder sql = new StringBuilder("select id, received_at, supplier_id, supplier_field_id from production_batches"
			+ " where received_at between ? and ?");
		List<Object> args = new ArrayList<>(List.of(range.from(), range.to()));
		if (truthy(supplierId)) {
			sql.append(" and supplier_id = ?");
			args.add(supplierId);
		}
		if (truthy(productTypeId)) {
			sql.append(" and exists (select * from production_batch_input_production_product_types where production_batches.id ="
				+ " production_batch_input_production_product_types.production_batch_id and production_product_type_id = ?)");
			args.add(productTypeId);
		}
		if (truthy(fieldId)) {
			sql.append(" and supplier_field_id = ?");
			args.add(fieldId);
		}
		List<Row> batches = db.select(sql.toString(), args.toArray());
		if (batches.isEmpty()) {
			return List.of();
		}
		Map<Long, Row> suppliers = Relations.byId(db.whereIn("suppliers", "id", Relations.column(batches, "supplier_id"), null));
		Map<Long, Row> fields = Relations.byId(db.whereIn("supplier_fields", "id", Relations.column(batches, "supplier_field_id"), null));
		List<Row> inputRows = db.whereIn("production_batch_input_production_product_types", "production_batch_id", Relations.ids(batches), "sort_order");
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id", Relations.column(inputRows, "production_product_type_id"), null));
		Map<Long, List<Row>> inputs = Relations.groupBy(inputRows, "production_batch_id");
		Map<Long, List<Row>> processes = new LinkedHashMap<>();
		if (!roles.isEmpty()) {
			for (Row p : db.select("select production_batch_id, production_stage_id, operation_data from batch_processes where production_batch_id in ("
				+ Relations.inList(Relations.ids(batches)) + ") and production_stage_id in (" + Relations.inList(roles.keySet())
				+ ") and status = ?", "completed")) {
				processes.computeIfAbsent(p.longValue("production_batch_id"), k -> new ArrayList<>()).add(p);
			}
		}
		List<BatchRow> out = new ArrayList<>();
		for (Row batch : batches) {
			Map<String, Double> values = new LinkedHashMap<>();
			List<Row> ordered = new ArrayList<>(processes.getOrDefault(batch.id(), List.of()));
			ordered.sort(Comparator.comparingLong(p -> roles.get(p.longValue("production_stage_id")).order()));
			for (Row process : ordered) {
				String role = roles.get(process.longValue("production_stage_id")).role();
				Map<?, ?> data = process.get("operation_data") instanceof Map<?, ?> m ? m : Map.of();
				Object value = switch (role) {
					case "pressed" -> data.get("input_quantity");
					case "acidity" -> acidity(data.get("attributes"));
					default -> data.get("quantity");
				};
				if (value != null && values.get(role) == null) {
					values.put(role, Php.floatval(value));
				}
			}
			List<Row> batchInputs = inputs.getOrDefault(batch.id(), List.of());
			Row type = batchInputs.isEmpty() ? null : types.get(batchInputs.getFirst().longValue("production_product_type_id"));
			Row supplier = batch.longValue("supplier_id") == null ? null : suppliers.get(batch.longValue("supplier_id"));
			Row field = batch.longValue("supplier_field_id") == null ? null : fields.get(batch.longValue("supplier_field_id"));
			Double pressed = values.get("pressed") != null ? values.get("pressed") : values.get("incoming");
			out.add(new BatchRow(batch.id(), batch.time("received_at"), batch.longValue("supplier_id"),
				supplier == null ? null : supplier.string("name"), supplier == null ? null : supplier.string("code"),
				batch.longValue("supplier_field_id"), field == null ? null : field.string("code"),
				type == null ? null : type.id(), type == null ? null : type.string("name"),
				values.get("incoming"), pressed, values.get("oil"), values.get("pomace"), values.get("acidity")));
		}
		return out.stream().filter(r -> r.oil() != null && r.pressed() != null && r.pressed() > 0).toList();
	}

	/** {@code collect($attributes)->firstWhere('attribute_key', 'acidity')['value']} */
	private static Object acidity(Object attributes) {
		Collection<?> list = attributes instanceof Map<?, ?> m ? m.values() : attributes instanceof List<?> l ? l : List.of();
		for (Object a : list) {
			if (a instanceof Map<?, ?> m && "acidity".equals(m.get("attribute_key") == null ? null : Php.strval(m.get("attribute_key")))) {
				return m.get("value");
			}
		}
		return null;
	}

	private record Role(String role, long order) {
	}

	/** Asama id -> gorev ve sira: gelen (hattin ilk zeytin tartimi), sikilan, yag, pirina, asitlik. */
	private Map<Long, Role> stageRoles() {
		List<Row> stages = db.select("select * from production_stages order by production_line_id asc, stage_order asc");
		List<Row> extensions = db.whereIn("production_stage_extensions", "production_stage_id", Relations.ids(stages), "sort_order");
		Map<Long, Row> extensionTypes = Relations.byId(db.whereIn("stage_extension_types", "id",
			Relations.column(extensions, "stage_extension_type_id"), null));
		List<Row> inputRows = db.whereIn("production_stage_extension_input_production_products", "production_stage_extension_id",
			Relations.ids(extensions), "sort_order");
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id", Relations.column(inputRows, "production_product_id"), null));
		Map<Long, List<Row>> byStage = Relations.groupBy(extensions, "production_stage_id");
		Map<Long, List<Row>> inputsByExtension = Relations.groupBy(inputRows, "production_stage_extension_id");
		Map<Long, Role> roles = new LinkedHashMap<>();
		java.util.Set<Long> seenIncoming = new java.util.HashSet<>();
		for (Row stage : stages) {
			for (Row extension : byStage.getOrDefault(stage.id(), List.of())) {
				if (!Boolean.TRUE.equals(Casts.bool(extension.get("is_enabled")))) {
					continue;
				}
				Row typeRow = extensionTypes.get(extension.longValue("stage_extension_type_id"));
				String type = typeRow == null ? null : typeRow.string("key");
				List<String> keys = new ArrayList<>();
				for (Row input : inputsByExtension.getOrDefault(extension.id(), List.of())) {
					Row product = products.get(input.longValue("production_product_id"));
					String key = product == null ? null : product.string("key");
					if (key != null && !key.isEmpty() && !"0".equals(key)) {
						keys.add(key);
					}
				}
				Long line = stage.longValue("production_line_id");
				String role = null;
				if ("weighing".equals(type) && keys.contains("olive") && !seenIncoming.contains(line)) {
					role = "incoming";
				}
				else if ("material_transformation".equals(type) && keys.contains("olive")) {
					role = "pressed";
				}
				else if ("weighing".equals(type) && keys.contains("olive_oil")) {
					role = "oil";
				}
				else if ("weighing".equals(type) && keys.contains("pomace")) {
					role = "pomace";
				}
				else if ("analysis".equals(type) && keys.contains("olive_oil")) {
					role = "acidity";
				}
				if (role == null) {
					continue;
				}
				if (role.equals("incoming")) {
					seenIncoming.add(line);
				}
				roles.put(stage.id(), new Role(role, Php.intval(stage.get("stage_order"))));
			}
		}
		return roles;
	}

	/** Toplamlar ve toplamdan hesaplanan randiman (PHP 8.4 round). */
	static Map<String, Object> totals(List<BatchRow> rows) {
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
		}
		for (BatchRow r : rows) {
			pomace += r.pomace() == null ? 0 : r.pomace();
		}
		for (BatchRow r : rows) {
			if (r.acidity() != null) {
				aciditySum += r.acidity();
				acidityCount++;
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("batch_count", rows.size());
		m.put("incoming_kg", Php.round(incoming, 1));
		m.put("pressed_kg", Php.round(pressed, 1));
		m.put("oil_kg", Php.round(oil, 1));
		m.put("pomace_kg", Php.round(pomace, 1));
		m.put("yield_pct", pressed > 0 ? Php.round(oil / pressed * 100, 2) : null);
		m.put("gross_yield_pct", incoming > 0 ? Php.round(oil / incoming * 100, 2) : null);
		m.put("avg_acidity", acidityCount == 0 ? null : Php.round(aciditySum / acidityCount, 2));
		return m;
	}

	// ---- siparis satirlari ------------------------------------------------------------------

	List<ItemRow> orderItems(LocalDate from, LocalDate to, Object customerId, Object finishedProductId) {
		StringBuilder sql = new StringBuilder("select * from order_items where ");
		List<Object> args = new ArrayList<>();
		if (truthy(finishedProductId)) {
			sql.append("finished_product_id = ? and ");
			args.add(finishedProductId);
		}
		sql.append("exists (select * from orders where order_items.order_id = orders.id and status != ? and order_date between ? and ?");
		args.add("cancelled");
		args.add(from.toString());
		args.add(to.toString());
		if (truthy(customerId)) {
			sql.append(" and customer_id = ?");
			args.add(customerId);
		}
		sql.append(")");
		List<Row> items = db.select(sql.toString(), args.toArray());
		Map<Long, Row> orders = Relations.byId(db.whereIn("orders", "id", Relations.column(items, "order_id"), null));
		Map<Long, Row> customers = Relations.byId(db.whereIn("customers", "id", Relations.column(List.copyOf(orders.values()), "customer_id"), null));
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(items, "finished_product_id"), null));
		List<ItemRow> out = new ArrayList<>();
		for (Row item : items) {
			Row order = orders.get(item.longValue("order_id"));
			Long customerKey = order == null ? null : order.longValue("customer_id");
			out.add(new ItemRow(item.longValue("order_id"), customerKey, customerKey == null ? null : customers.get(customerKey),
				order == null ? null : date(order.get("order_date")), item.longValue("finished_product_id"),
				item.longValue("finished_product_id") == null ? null : products.get(item.longValue("finished_product_id")),
				Php.intval(item.get("quantity"))));
		}
		return out;
	}

	static Map<String, Object> orderTotals(List<ItemRow> items) {
		long bottles = 0;
		double litres = 0;
		for (ItemRow i : items) {
			bottles += i.quantity();
		}
		for (ItemRow i : items) {
			litres += i.quantity() * (i.product() == null ? 0 : Php.floatval(Casts.decimal(i.product().get("volume_litre"), 4)));
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("bottle_count", bottles);
		m.put("litres", Php.round(litres, 1));
		return m;
	}

	private static List<Map<String, Object>> productBreakdown(List<ItemRow> items) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (List<ItemRow> group : groupBy(items, i -> i.finishedProductId() == null ? "" : i.finishedProductId()).values()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("finished_product_id", group.getFirst().finishedProductId());
			m.put("name", group.getFirst().product() == null ? null : group.getFirst().product().get("name"));
			m.putAll(orderTotals(group));
			out.add(m);
		}
		sortByDesc(out, "litres");
		return out;
	}

	static int orderCount(List<ItemRow> items) {
		return new LinkedHashSet<>(items.stream().map(ItemRow::orderId).toList()).size();
	}

	/** {@code company_name ?: name} */
	static String customerName(Row customer) {
		if (customer == null) {
			return null;
		}
		String company = customer.string("company_name");
		return company != null && !company.isEmpty() && !"0".equals(company) ? company : customer.string("name");
	}

	// ---- zaman dilimleri ---------------------------------------------------------------------

	/**
	 * Carbon 3: {@code diffInDays(gunSonu) + 1 = N + 0.99..}. Gun gun yalnizca N <= 30, hafta N <= 119, ustu ay
	 * (Laravel'de 31 gunluk aralik haftalik).
	 */
	static String bucketFor(Range range) {
		long n = range.inclusiveDays();
		if (n <= 30) {
			return DAY;
		}
		return n <= 119 ? WEEK : MONTH;
	}

	/** Gun: Y-m-d; hafta: pazartesi (tr); ay: Y-m. */
	static String bucketKey(LocalDate date, String bucket) {
		return switch (bucket) {
			case DAY -> date.toString();
			case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
			default -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
		};
	}

	/** Araligin butun dilimleri, bos olanlar dahil. */
	static List<Map<String, Object>> emptyBuckets(Range range, String bucket) {
		List<Map<String, Object>> slots = new ArrayList<>();
		LocalDate cursor = switch (bucket) {
			case DAY -> range.fromDate();
			case WEEK -> range.fromDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			default -> range.fromDate().withDayOfMonth(1);
		};
		while (!cursor.isAfter(range.toDate())) {
			Map<String, Object> slot = new LinkedHashMap<>();
			slot.put("key", bucketKey(cursor, bucket));
			slot.put("start", cursor.toString());
			slots.add(slot);
			cursor = switch (bucket) {
				case DAY -> cursor.plusDays(1);
				case WEEK -> cursor.plusWeeks(1);
				default -> cursor.plusMonths(1);
			};
		}
		return slots;
	}

	// ---- yardimcilar --------------------------------------------------------------------------

	/** {@code groupBy}: ilk gorulme sirasiyla. */
	static <T> Map<Object, List<T>> groupBy(List<T> items, Function<T, Object> key) {
		Map<Object, List<T>> out = new LinkedHashMap<>();
		for (T item : items) {
			out.computeIfAbsent(key.apply(item), k -> new ArrayList<>()).add(item);
		}
		return out;
	}

	/** {@code sortByDesc}: kararli, null en sonda (PHP'de null < sayi). */
	static void sortByDesc(List<Map<String, Object>> rows, String key) {
		rows.sort((a, b) -> Double.compare(number(b.get(key)), number(a.get(key))));
	}

	private static double number(Object value) {
		return value == null ? Double.NEGATIVE_INFINITY : ((Number) value).doubleValue();
	}

	/** PHP truthiness (when() kosulu). */
	static boolean truthy(Object value) {
		return value != null && !"".equals(value) && !"0".equals(value) && !(value instanceof Number n && n.doubleValue() == 0)
			&& !Boolean.FALSE.equals(value);
	}

	static LocalDate date(Object value) {
		return switch (value) {
			case null -> null;
			case LocalDate d -> d;
			case LocalDateTime t -> t.toLocalDate();
			case java.sql.Date d -> d.toLocalDate();
			default -> LocalDate.parse(Php.strval(value).substring(0, 10));
		};
	}

	/**
	 * Aralik: {@code from} gun basi, {@code to} gun sonu (sorguya "Y-m-d 23:59:59").
	 * @param inclusiveDays iki uc dahil gun sayisi
	 */
	record Range(LocalDate fromDate, LocalDate toDate) {

		LocalDateTime from() {
			return fromDate.atStartOfDay();
		}

		LocalDateTime to() {
			return toDate.atTime(23, 59, 59);
		}

		long inclusiveDays() {
			return java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
		}

	}

}
