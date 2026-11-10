package com.olivamore.api.analytics.dailysummary;

import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.AwaitingProcess;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.BatchRow;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.CurrentStage;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.FillingRuns;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.OrderRow;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.Orders;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.OutOfStock;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.Production;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.RunRow;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.Stages;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.Stock;
import com.olivamore.api.analytics.dailysummary.DailySummaryResponse.Unplaced;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** "Bugun ne oldu" ekraninin bolumleri; her bolum yalnizca ekranda gorunen alanlari verir. */
@Service
@Transactional(readOnly = true)
class DailySummaryService {

	private static final Set<String> OPEN_STATUSES = Set.of("in_progress", "awaiting_approval", "failed");

	private static final Set<String> DONE_STATUSES = Set.of("completed", "skipped");

	private static final String BATCH_COLUMNS = """
		select b.id, b.batch_code, b.status, b.quantity_kg, b.received_at, b.started_at, b.completed_at,
			(select s.name from suppliers s where s.id = b.supplier_id) as supplier,
			(select l.name from production_lines l where l.id = b.production_line_id) as production_line
		from production_batches b
		""";

	private static final String RUN_COLUMNS = """
		select r.id, r.status, r.quantity, r.finished_product_id, r.started_at, r.completed_at,
			(select p.name from finished_products p where p.id = r.finished_product_id) as finished_product,
			(select p.volume_litre from finished_products p where p.id = r.finished_product_id) as volume_litre,
			(select u.name from storage_units u where u.id = r.source_storage_unit_id) as source_storage_unit
		from finished_product_runs r
		""";

	private static final String ORDER_COLUMNS = """
		select o.id, o.status, o.order_date,
			(select c.name from customers c where c.id = o.customer_id) as customer,
			(select count(*) from order_items i where i.order_id = o.id) as item_count
		from orders o
		""";

	private record Batch(long id, String batchCode, String status, BigDecimal quantityKg, LocalDateTime receivedAt,
		LocalDateTime startedAt, LocalDateTime completedAt, String supplier, String productionLine) {

		BatchRow row(CurrentStage stage) {
			return new BatchRow(id, batchCode, status, quantityKg == null ? null : quantityKg.doubleValue(), supplier,
				productionLine, receivedAt, startedAt, completedAt, stage);
		}

	}

	private record Process(long id, long productionBatchId, String status, String name, Integer stageOrder) {

		long order() {
			return stageOrder == null ? 0 : stageOrder;
		}

	}

	private record Run(long id, String status, Long quantity, Long finishedProductId, LocalDateTime startedAt,
		LocalDateTime completedAt, String finishedProduct, BigDecimal volumeLitre, String sourceStorageUnit) {

		RunRow row() {
			return new RunRow(id, status, quantity == null ? 0 : quantity, finishedProductId, finishedProduct,
				volumeLitre == null ? null : volumeLitre.doubleValue(), sourceStorageUnit, startedAt, completedAt);
		}

	}

	private record Order(long id, String status, LocalDate orderDate, String customer, long itemCount) {

		OrderRow row() {
			return new OrderRow(id, status, customer, orderDate == null ? null : orderDate.toString(), itemCount);
		}

	}

	private record StockRow(long id, String name, Long totalStock, Long storedStock, Long awaitingShipmentStock) {

		long total() {
			return totalStock == null ? 0 : totalStock;
		}

		long unstored() {
			long stored = storedStock == null ? 0 : storedStock;
			long awaiting = awaitingShipmentStock == null ? 0 : awaitingShipmentStock;
			return Math.max(0, total() - stored - awaiting);
		}

	}

	private final JdbcClient jdbc;

	DailySummaryService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Giren ve biten o gunun; uretimdeki partiler tarihe bakmaz. */
	Production production(LocalDate date) {
		List<BatchRow> entered = jdbc.sql(BATCH_COLUMNS + " where b.received_at::date = :date order by b.received_at desc")
			.param("date", date).query(Batch.class).list().stream().map(b -> b.row(null)).toList();
		List<Batch> active = jdbc.sql(BATCH_COLUMNS + " where b.status = 'in_progress' order by b.received_at asc")
			.query(Batch.class).list();
		List<BatchRow> completed = jdbc
			.sql(BATCH_COLUMNS + " where b.completed_at::date = :date order by b.completed_at desc")
			.param("date", date).query(Batch.class).list().stream().map(b -> b.row(null)).toList();
		Map<Long, CurrentStage> stages = currentStages(active.stream().map(Batch::id).toList());
		return new Production(entered, active.stream().map(b -> b.row(stages.get(b.id()))).toList(), completed);
	}

	/** Su anki asama: yuruyen, onay bekleyen ya da takilan ilk asama; yoksa son kapanan; o da yoksa ilk. */
	private Map<Long, CurrentStage> currentStages(List<Long> batchIds) {
		if (batchIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<Process>> byBatch = jdbc.sql("""
			select p.id, p.production_batch_id, p.status, s.name, s.stage_order
			from batch_processes p
			left join production_stages s on s.id = p.production_stage_id
			where p.production_batch_id in (:ids)
			order by p.id
			""").param("ids", batchIds).query(Process.class).list().stream()
			.collect(Collectors.groupingBy(Process::productionBatchId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, CurrentStage> stages = new LinkedHashMap<>();
		byBatch.forEach((batchId, processes) -> {
			List<Process> sorted = new ArrayList<>(processes);
			sorted.sort(Comparator.comparingLong(Process::order));
			Process open = sorted.stream().filter(p -> OPEN_STATUSES.contains(p.status())).findFirst().orElse(null);
			List<Process> done = sorted.stream().filter(p -> DONE_STATUSES.contains(p.status())).toList();
			Process current = open != null ? open : !done.isEmpty() ? done.getLast() : sorted.getFirst();
			stages.put(batchId, new CurrentStage(current.id(), current.name(), current.stageOrder(), current.status(),
				done.size(), sorted.size()));
		});
		return stages;
	}

	/** Onay bekleyenler tarihten bagimsiz. */
	Stages stages(LocalDate date) {
		long completedToday = jdbc.sql("""
			select count(*) from batch_processes
			where status in ('completed', 'skipped') and completed_at::date = :date
			""").param("date", date).query(Long.class).single();
		List<AwaitingProcess> awaiting = jdbc.sql("""
			select p.id, p.production_batch_id,
				(select b.batch_code from production_batches b where b.id = p.production_batch_id) as batch_code,
				(select s.name from production_stages s where s.id = p.production_stage_id) as stage_name,
				p.submitted_at
			from batch_processes p
			where p.status = 'awaiting_approval'
			order by p.submitted_at asc
			""").query(AwaitingProcess.class).list();
		return new Stages(completedToday, awaiting);
	}

	FillingRuns fillingRuns(LocalDate date) {
		List<RunRow> completed = runs(jdbc
			.sql(RUN_COLUMNS + " where r.status = 'completed' and r.completed_at::date = :date order by r.completed_at desc")
			.param("date", date).query(Run.class).list());
		List<RunRow> inProgress = runs(jdbc.sql(RUN_COLUMNS + " where r.status = 'in_progress' order by r.started_at asc")
			.query(Run.class).list());
		List<RunRow> planned = runs(jdbc.sql(RUN_COLUMNS + " where r.status = 'planned' order by r.id asc")
			.query(Run.class).list());
		return new FillingRuns(completed, inProgress, planned);
	}

	private static List<RunRow> runs(List<Run> runs) {
		return runs.stream().map(Run::row).toList();
	}

	/** Bugun sevk edilenler durum gecmisinden; acik siparisler tarihten bagimsiz; bugune planlananlar. */
	Orders orders(LocalDate date) {
		List<OrderRow> completed = orders(jdbc.sql(ORDER_COLUMNS + """
			 where exists (select 1 from order_status_histories h
				where h.order_id = o.id and h.to_status = 'shipped' and h.changed_at::date = :date)
			order by o.order_date desc
			""").param("date", date).query(Order.class).list());
		List<OrderRow> open = orders(jdbc
			.sql(ORDER_COLUMNS + " where o.status not in ('shipped', 'cancelled') order by o.order_date asc")
			.query(Order.class).list());
		List<OrderRow> planned = orders(jdbc.sql(ORDER_COLUMNS + " where o.order_date::date = :date order by o.id asc")
			.param("date", date).query(Order.class).list());
		return new Orders(completed, open, planned);
	}

	private static List<OrderRow> orders(List<Order> orders) {
		return orders.stream().map(Order::row).toList();
	}

	/** Stokta olmayan ve depoya yerlestirilmemis aktif urunler (ada gore). */
	Stock stock() {
		List<StockRow> products = jdbc.sql("""
			select p.id, p.name, p.total_stock,
				(select sum(s.quantity) from finished_product_storage_stocks s
					where s.finished_product_id = p.id) as stored_stock,
				(select sum(m.quantity) from finished_product_stock_movements m
					where m.finished_product_id = p.id and m.movement_type = 'order_pick' and exists
					(select 1 from orders o where o.id = m.order_id and o.status = 'ready')) as awaiting_shipment_stock
			from finished_products p
			where p.is_active = true
			order by p.name asc
			""").query(StockRow.class).list();
		List<OutOfStock> outOfStock = products.stream().filter(p -> p.total() <= 0)
			.map(p -> new OutOfStock(p.id(), p.name(), p.total())).toList();
		List<Unplaced> unplaced = products.stream().filter(p -> p.unstored() > 0)
			.map(p -> new Unplaced(p.id(), p.name(), p.total(), p.unstored())).toList();
		return new Stock(outOfStock, unplaced);
	}

}
