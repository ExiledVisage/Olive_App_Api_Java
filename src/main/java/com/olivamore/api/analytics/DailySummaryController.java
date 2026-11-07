package com.olivamore.api.analytics;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Permissions;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.RequestInput;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code DailySummaryController} + {@code DailySummaryService}: acilis ekrani. Ayri izin istemez; her
 * bolum kullanicinin iznine bakar, izin yoksa govdede hic yer almaz.
 */
@RestController
class DailySummaryController {

	private static final Set<String> OPEN_STATUSES = Set.of("in_progress", "awaiting_approval", "failed");
	private static final Set<String> DONE_STATUSES = Set.of("completed", "skipped");

	private final Db db;
	private final LaravelValidator validator;
	private final Permissions permissions;

	DailySummaryController(Db db, LaravelValidator validator, Permissions permissions) {
		this.db = db;
		this.validator = validator;
		this.permissions = permissions;
	}

	/** {@code ?date=} (verilmezse bugun). */
	@GetMapping("/api/daily-summary")
	Map<String, Object> show(RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of().field("date", "nullable|date"));
		LocalDate date = v.get("date") == null ? Timestamps.now().toLocalDate()
			: PhpDates.parse(Php.strval(v.get("date"))).orElseThrow().toLocalDate();
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("date", date.toString());
		if (permissions.can(user, "production_batch.view")) {
			summary.put("production", production(date));
		}
		if (permissions.can(user, "batch_process.view")) {
			summary.put("stages", stages(date));
		}
		if (permissions.can(user, "finished_product_run.view")) {
			summary.put("filling_runs", fillingRuns(date));
		}
		if (permissions.can(user, "order.view")) {
			summary.put("orders", orders(date));
		}
		if (permissions.can(user, "finished_product.view")) {
			summary.put("stock", stock());
		}
		return Map.of("data", summary);
	}

	// ---- uretim -------------------------------------------------------------------------

	/** Giren ve biten o gunun; uretimdeki partiler tarihe bakmaz. */
	private Map<String, Object> production(LocalDate date) {
		List<Row> entered = db.select("select * from production_batches where received_at::date = ? order by received_at desc", date.toString());
		List<Row> active = db.select("select * from production_batches where status = ? order by received_at asc", "in_progress");
		List<Row> completed = db.select("select * from production_batches where completed_at::date = ? order by completed_at desc", date.toString());
		Map<Long, Map<String, Object>> stages = currentStages(Relations.ids(active));
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("entered_today", batchRows(entered, Map.of()));
		out.put("in_production", batchRows(active, stages));
		out.put("completed_today", batchRows(completed, Map.of()));
		return out;
	}

	/** Su anki asama: yuruyen, onay bekleyen ya da takilan ilk asama; yoksa son kapanan; o da yoksa ilk. */
	private Map<Long, Map<String, Object>> currentStages(List<Long> batchIds) {
		Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
		if (batchIds.isEmpty()) {
			return out;
		}
		List<Row> processes = db.whereIn("batch_processes", "production_batch_id", batchIds, null);
		Map<Long, Row> stages = Relations.byId(db.whereIn("production_stages", "id", Relations.column(processes, "production_stage_id"), null));
		for (Map.Entry<Long, List<Row>> e : Relations.groupBy(processes, "production_batch_id").entrySet()) {
			List<Row> sorted = new ArrayList<>(e.getValue());
			sorted.sort(Comparator.comparingLong(p -> stageOrder(stages.get(p.longValue("production_stage_id")))));
			Row open = sorted.stream().filter(p -> OPEN_STATUSES.contains(p.string("status"))).findFirst().orElse(null);
			List<Row> done = sorted.stream().filter(p -> DONE_STATUSES.contains(p.string("status"))).toList();
			Row current = open != null ? open : !done.isEmpty() ? done.getLast() : sorted.isEmpty() ? null : sorted.getFirst();
			Row stage = current == null ? null : stages.get(current.longValue("production_stage_id"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("batch_process_id", current == null ? null : current.get("id"));
			m.put("name", stage == null ? null : stage.get("name"));
			m.put("order", stage == null ? null : stage.get("stage_order"));
			m.put("status", current == null ? null : current.get("status"));
			m.put("done_count", done.size());
			m.put("total_count", sorted.size());
			out.put(e.getKey(), m);
		}
		return out;
	}

	private static long stageOrder(Row stage) {
		return stage == null || stage.get("stage_order") == null ? 0 : Php.intval(stage.get("stage_order"));
	}

	private List<Map<String, Object>> batchRows(List<Row> batches, Map<Long, Map<String, Object>> stages) {
		Map<Long, Row> suppliers = Relations.byId(db.whereIn("suppliers", "id", Relations.column(batches, "supplier_id"), null));
		Map<Long, Row> lines = Relations.byId(db.whereIn("production_lines", "id", Relations.column(batches, "production_line_id"), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row b : batches) {
			Row supplier = b.longValue("supplier_id") == null ? null : suppliers.get(b.longValue("supplier_id"));
			Row line = b.longValue("production_line_id") == null ? null : lines.get(b.longValue("production_line_id"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", b.get("id"));
			m.put("batch_code", b.get("batch_code"));
			m.put("status", b.get("status"));
			m.put("quantity_kg", b.get("quantity_kg") == null ? null : Php.floatval(Casts.decimal(b.get("quantity_kg"), 4)));
			m.put("supplier", supplier == null ? null : supplier.get("name"));
			m.put("production_line", line == null ? null : line.get("name"));
			m.put("received_at", b.get("received_at"));
			m.put("started_at", b.get("started_at"));
			m.put("completed_at", b.get("completed_at"));
			m.put("current_stage", stages.get(b.id()));
			out.add(m);
		}
		return out;
	}

	// ---- asamalar -------------------------------------------------------------------------

	/** Onay bekleyenler tarihten bagimsiz. */
	private Map<String, Object> stages(LocalDate date) {
		long completedToday = db.count("select count(*) from batch_processes where status in (?, ?) and completed_at::date = ?",
			"completed", "skipped", date.toString());
		List<Row> awaiting = db.select("select * from batch_processes where status = ? order by submitted_at asc", "awaiting_approval");
		Map<Long, Row> stages = Relations.byId(db.whereIn("production_stages", "id", Relations.column(awaiting, "production_stage_id"), null));
		Map<Long, Row> batches = Relations.byId(db.whereIn("production_batches", "id", Relations.column(awaiting, "production_batch_id"), null));
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Row p : awaiting) {
			Row stage = stages.get(p.longValue("production_stage_id"));
			Row batch = batches.get(p.longValue("production_batch_id"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", p.get("id"));
			m.put("production_batch_id", p.get("production_batch_id"));
			m.put("batch_code", batch == null ? null : batch.get("batch_code"));
			m.put("stage_name", stage == null ? null : stage.get("name"));
			m.put("submitted_at", p.get("submitted_at"));
			rows.add(m);
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("completed_today", completedToday);
		out.put("awaiting_approval", rows);
		return out;
	}

	// ---- dolum ------------------------------------------------------------------------------

	private Map<String, Object> fillingRuns(LocalDate date) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("completed_today", runRows(db.select("select * from finished_product_runs where status = ? and completed_at::date = ?"
			+ " order by completed_at desc", "completed", date.toString())));
		out.put("in_progress", runRows(db.select("select * from finished_product_runs where status = ? order by started_at asc", "in_progress")));
		out.put("planned", runRows(db.select("select * from finished_product_runs where status = ? order by id asc", "planned")));
		return out;
	}

	private List<Map<String, Object>> runRows(List<Row> runs) {
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(runs, "finished_product_id"), null));
		Map<Long, Row> units = Relations.byId(db.whereIn("storage_units", "id", Relations.column(runs, "source_storage_unit_id"), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row r : runs) {
			Row product = r.longValue("finished_product_id") == null ? null : products.get(r.longValue("finished_product_id"));
			Row unit = r.longValue("source_storage_unit_id") == null ? null : units.get(r.longValue("source_storage_unit_id"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", r.get("id"));
			m.put("status", r.get("status"));
			m.put("quantity", Php.intval(r.get("quantity")));
			m.put("finished_product_id", r.get("finished_product_id"));
			m.put("finished_product", product == null ? null : product.get("name"));
			m.put("volume_litre", product == null || product.get("volume_litre") == null ? null
				: Php.floatval(Casts.decimal(product.get("volume_litre"), 4)));
			m.put("source_storage_unit", unit == null ? null : unit.get("name"));
			m.put("started_at", r.get("started_at"));
			m.put("completed_at", r.get("completed_at"));
			out.add(m);
		}
		return out;
	}

	// ---- siparisler ---------------------------------------------------------------------------

	/** Bugun sevk edilenler durum gecmisinden; acik siparisler tarihten bagimsiz; bugune planlananlar. */
	private Map<String, Object> orders(LocalDate date) {
		String select = "select orders.*, (select count(*) from order_items where orders.id = order_items.order_id) as items_count from orders";
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("completed_today", orderRows(db.select(select + " where exists (select * from order_status_histories"
			+ " where orders.id = order_status_histories.order_id and to_status = ? and changed_at::date = ?) order by order_date desc",
			"shipped", date.toString())));
		out.put("open", orderRows(db.select(select + " where status not in (?, ?) order by order_date asc", "shipped", "cancelled")));
		out.put("planned_today", orderRows(db.select(select + " where order_date::date = ? order by id asc", date.toString())));
		return out;
	}

	private List<Map<String, Object>> orderRows(List<Row> orders) {
		Map<Long, Row> customers = Relations.byId(db.whereIn("customers", "id", Relations.column(orders, "customer_id"), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row o : orders) {
			Row customer = o.longValue("customer_id") == null ? null : customers.get(o.longValue("customer_id"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", o.get("id"));
			m.put("status", o.get("status"));
			m.put("customer", customer == null ? null : customer.get("name"));
			LocalDate orderDate = Analytics.date(o.get("order_date"));
			m.put("order_date", orderDate == null ? null : orderDate.toString());
			m.put("item_count", Php.intval(o.get("items_count")));
			out.add(m);
		}
		return out;
	}

	// ---- stok -----------------------------------------------------------------------------------

	/** Stokta olmayan ve depoya yerlestirilmemis aktif urunler (ada gore). */
	private Map<String, Object> stock() {
		List<Row> products = db.select("select finished_products.*, (select sum(finished_product_storage_stocks.quantity)"
			+ " from finished_product_storage_stocks where finished_products.id = finished_product_storage_stocks.finished_product_id)"
			+ " as stored_stock, (select sum(finished_product_stock_movements.quantity) from finished_product_stock_movements"
			+ " where finished_products.id = finished_product_stock_movements.finished_product_id and movement_type = ? and exists"
			+ " (select * from orders where finished_product_stock_movements.order_id = orders.id and status = ?)) as awaiting_shipment_stock"
			+ " from finished_products where is_active = ? order by name asc", "order_pick", "ready", true);
		List<Map<String, Object>> outOfStock = new ArrayList<>();
		List<Map<String, Object>> unplaced = new ArrayList<>();
		for (Row p : products) {
			long total = Php.intval(p.get("total_stock"));
			if (total <= 0) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", p.get("id"));
				m.put("name", p.get("name"));
				m.put("total_stock", total);
				outOfStock.add(m);
			}
			long unstored = Math.max(0, total - Php.intval(p.get("stored_stock")) - Php.intval(p.get("awaiting_shipment_stock")));
			if (unstored > 0) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", p.get("id"));
				m.put("name", p.get("name"));
				m.put("total_stock", total);
				m.put("unstored_stock", unstored);
				unplaced.add(m);
			}
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("out_of_stock", outOfStock);
		out.put("unplaced", unplaced);
		return out;
	}

}
