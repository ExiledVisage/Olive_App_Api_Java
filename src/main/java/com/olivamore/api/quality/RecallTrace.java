package com.olivamore.api.quality;

import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Geri cagirmanin ileri izi (docs/QUALITY.md 3).
 * <ul>
 * <li>Teslimat: kendisi ve gruplari -> materyalleri ve torunlari -> girdigi tank materyalleri -> aktarmalarla diger
 * tank materyalleri (aktarma giristen sonraysa) -> o tanklardan giristen sonra cekilen ve materyallerden dogrudan
 * cekilen dolumlar.</li>
 * <li>Tank materyali: butun dolumlari ve aktarmalari.</li>
 * <li>Dolum: kendisi.</li>
 * </ul>
 * Dolumdan siparise: raflar kosu bazinda tutulmadigi icin kosunun konuldugu raftan, raf bosalana kadar cikan
 * siparisler. Raf o sure boyunca yalnizca bu kosunun siseleriyle doluysa (oncesi bos, araya giris yok) kesin, degilse
 * olasi. Raflar arasi aktarma izlenir.
 */
@Component
class RecallTrace {

	record Batch(long id, String code) {
	}

	/** @param enteredAt iz bu tanka ilk ne zaman girdi (tank materyali kaynaksa null: hepsi) */
	record Tank(long materialId, Long storageUnitId, String name, LocalDateTime enteredAt) {
	}

	record Run(long id, long productId, String product, int quantity, String status, LocalDateTime completedAt) {
	}

	record OrderLine(long orderId, String orderStatus, long customerId, String customer, String phone,
		long productId, String product, int quantity, boolean definite) {
	}

	record Result(String source, List<Batch> batches, List<Tank> tanks, List<Run> runs, List<OrderLine> orders) {
	}

	private record Entry(long materialId, Long storageUnitId, LocalDateTime movedAt) {
	}

	private record Move(long id, long slotId, long productId, String movementType, long quantity,
		UUID transferReference, Long orderId, LocalDateTime movedAt) {
	}

	private record Pick(long orderId, long productId, long quantity, boolean definite) {
	}

	private final JdbcClient jdbc;

	RecallTrace(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Result trace(String sourceType, long sourceId) {
		return switch (sourceType) {
			case "delivery" -> delivery(sourceId);
			case "tank_material" -> tankMaterial(sourceId);
			case "run" -> run(sourceId);
			default -> throw new IllegalArgumentException(sourceType);
		};
	}

	/** Kaynagin gosterilecek adi; bulunamazsa 404. */
	String sourceLabel(String sourceType, long sourceId) {
		String sql = switch (sourceType) {
			case "delivery" -> "select batch_code from production_batches where id = :id and parent_batch_id is null";
			case "tank_material" -> """
				select concat_ws(' · ', u.name, t.name) from materials m
				join production_product_types t on t.id = m.production_product_type_id
				left join storage_units u on u.id = m.current_location_id and m.current_location_type = 'storage_unit'
				where m.id = :id and m.origin_type = 'TANK'""";
			case "run" -> """
				select p.name || ' · dolum #' || r.id from finished_product_runs r
				join finished_products p on p.id = r.finished_product_id where r.id = :id""";
			default -> throw new IllegalArgumentException(sourceType);
		};
		String type = switch (sourceType) {
			case "delivery" -> "ProductionBatch";
			case "tank_material" -> "Material";
			default -> "FinishedProductRun";
		};
		return jdbc.sql(sql).param("id", sourceId).query(String.class).optional()
			.orElseThrow(() -> new ModelNotFoundException(type, sourceId));
	}

	private Result delivery(long batchId) {
		String label = sourceLabel("delivery", batchId);
		List<Batch> batches = jdbc.sql("""
			select id, batch_code as code from production_batches where id = :id or parent_batch_id = :id order by id
			""").param("id", batchId).query(Batch.class).list();
		List<Long> materials = jdbc.sql("""
			with recursive tree as (
				select id from materials where production_batch_id in (:batches) and origin_type <> 'TANK'
				union
				select c.id from materials c join tree on c.parent_material_id = tree.id where c.origin_type <> 'TANK')
			select id from tree
			""").param("batches", batches.stream().map(Batch::id).toList()).query(Long.class).list();
		Map<Long, Entry> tanks = new LinkedHashMap<>();
		if (!materials.isEmpty()) {
			jdbc.sql("""
				select material_id, storage_unit_id, min(moved_at) as moved_at from storage_movements
				where source_material_id in (:ids) and material_id is not null
				group by material_id, storage_unit_id order by min(moved_at)
				""").param("ids", materials).query(Entry.class).list()
				.forEach(e -> tanks.merge(e.materialId(), e, RecallTrace::earlier));
		}
		followTransfers(tanks);
		Set<Long> runs = new HashSet<>();
		if (!materials.isEmpty()) {
			runs.addAll(jdbc.sql("""
				select distinct finished_product_run_id from finished_product_run_materials
				where source_material_id in (:ids)
				""").param("ids", materials).query(Long.class).list());
		}
		runs.addAll(tankRuns(tanks));
		return result(label, batches, tanks, runs);
	}

	private Result tankMaterial(long materialId) {
		String label = sourceLabel("tank_material", materialId);
		Map<Long, Entry> tanks = new LinkedHashMap<>();
		Long unit = jdbc.sql("""
			select storage_unit_id from storage_movements where material_id = :id order by moved_at, id limit 1
			""").param("id", materialId).query(Long.class).optional().orElse(null);
		tanks.put(materialId, new Entry(materialId, unit, null));
		followTransfers(tanks);
		return result(label, List.of(), tanks, tankRuns(tanks));
	}

	private Result run(long runId) {
		return result(sourceLabel("run", runId), List.of(), Map.of(), Set.of(runId));
	}

	/** Tank materyalinden aktarmayla girilen tank materyalleri (giristen sonraki aktarmalar). */
	private void followTransfers(Map<Long, Entry> tanks) {
		Deque<Long> queue = new ArrayDeque<>(tanks.keySet());
		while (!queue.isEmpty()) {
			Entry from = tanks.get(queue.poll());
			List<Entry> next = jdbc.sql("""
				select material_id, storage_unit_id, min(moved_at) as moved_at from storage_movements
				where source_material_id = :id and movement_type = 'TRANSFER_IN' and material_id is not null
					and (cast(:after as timestamp) is null or moved_at >= :after)
				group by material_id, storage_unit_id
				""").param("id", from.materialId()).param("after", from.movedAt()).query(Entry.class).list();
			for (Entry entry : next) {
				Entry known = tanks.get(entry.materialId());
				if (known == null || (known.movedAt() != null && entry.movedAt().isBefore(known.movedAt()))) {
					tanks.put(entry.materialId(), entry);
					queue.add(entry.materialId());
				}
			}
		}
	}

	/** Tank materyallerinden iz girdikten sonra cekilen dolumlar. */
	private Set<Long> tankRuns(Map<Long, Entry> tanks) {
		Set<Long> runs = new HashSet<>();
		for (Entry entry : tanks.values()) {
			runs.addAll(jdbc.sql("""
				select distinct finished_product_run_id from storage_movements
				where material_id = :id and finished_product_run_id is not null
					and (cast(:after as timestamp) is null or moved_at >= :after)
				""").param("id", entry.materialId()).param("after", entry.movedAt()).query(Long.class).list());
		}
		return runs;
	}

	private Result result(String label, List<Batch> batches, Map<Long, Entry> entries, Set<Long> runIds) {
		List<Tank> tanks = new ArrayList<>();
		if (!entries.isEmpty()) {
			Map<Long, String> names = new HashMap<>();
			List<Long> unitIds = entries.values().stream().map(Entry::storageUnitId).filter(Objects::nonNull)
				.distinct().toList();
			if (!unitIds.isEmpty()) {
				jdbc.sql("select id, name from storage_units where id in (:ids)").param("ids", unitIds)
					.query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
					.forEach(e -> names.put(e.getKey(), e.getValue()));
			}
			entries.values().forEach(e -> tanks.add(new Tank(e.materialId(), e.storageUnitId(),
				e.storageUnitId() == null ? null : names.get(e.storageUnitId()), e.movedAt())));
		}
		List<Run> runs = runIds.isEmpty() ? List.of() : jdbc.sql("""
			select r.id, r.finished_product_id as product_id, p.name as product, r.quantity, r.status, r.completed_at
			from finished_product_runs r join finished_products p on p.id = r.finished_product_id
			where r.id in (:ids) and r.status <> 'cancelled' order by coalesce(r.completed_at, r.created_at), r.id
			""").param("ids", runIds).query(Run.class).list();
		return new Result(label, batches, tanks, runs, orders(runs));
	}

	/** Kosularin siselerinin cikabildigi siparisler, siparis ve urun basina. */
	private List<OrderLine> orders(List<Run> runs) {
		Map<String, List<Move>> cache = new HashMap<>();
		Set<Long> visited = new HashSet<>();
		List<Pick> picks = new ArrayList<>();
		for (Run run : runs) {
			List<Move> placements = jdbc.sql("""
				select id, finished_product_storage_slot_id as slot_id, finished_product_id as product_id,
					movement_type,
					quantity, transfer_reference, order_id, moved_at
				from finished_product_stock_movements
				where finished_product_run_id = :run and movement_type = 'stock_in' order by moved_at, id
				""").param("run", run.id()).query(Move.class).list();
			for (Move placement : placements) {
				walk(placement, true, cache, visited, picks);
			}
		}
		Map<String, Pick> merged = new LinkedHashMap<>();
		for (Pick pick : picks) {
			merged.merge(pick.orderId() + ":" + pick.productId(), pick, (a, b) -> new Pick(a.orderId(), a.productId(),
				a.quantity() + b.quantity(), a.definite() && b.definite()));
		}
		if (merged.isEmpty()) {
			return List.of();
		}
		record OrderInfo(long id, String status, long customerId, String customer, String phone) {
		}
		Map<Long, OrderInfo> infos = new HashMap<>();
		jdbc.sql("""
			select o.id, o.status, c.id as customer_id,
				case when coalesce(c.company_name, '') in ('', '0') then c.name else c.company_name end as customer,
				c.phone
			from orders o join customers c on c.id = o.customer_id where o.id in (:ids)
			""").param("ids", merged.values().stream().map(Pick::orderId).distinct().toList())
			.query(OrderInfo.class).list().forEach(o -> infos.put(o.id(), o));
		Map<Long, String> products = new HashMap<>();
		jdbc.sql("select id, name from finished_products where id in (:ids)")
			.param("ids", merged.values().stream().map(Pick::productId).distinct().toList())
			.query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
			.forEach(e -> products.put(e.getKey(), e.getValue()));
		return merged.values().stream().filter(p -> infos.containsKey(p.orderId())).map(p -> {
			OrderInfo o = infos.get(p.orderId());
			return new OrderLine(o.id(), o.status(), o.customerId(), o.customer(), o.phone(), p.productId(),
				products.get(p.productId()), (int) p.quantity(), p.definite());
		}).sorted(Comparator.comparing(OrderLine::definite).reversed().thenComparing(OrderLine::customer,
			Comparator.nullsLast(String::compareTo)).thenComparingLong(OrderLine::orderId)).toList();
	}

	/**
	 * Giris hareketinden itibaren rafin bu urundeki hareketleri, raf bosalana kadar: siparis cikislari toplanir,
	 * aktarmalar karsi rafta surdurulur. Oncesinde stok ya da araya giris varsa kesinlik duser.
	 */
	private void walk(Move start, boolean exact, Map<String, List<Move>> cache, Set<Long> visited, List<Pick> picks) {
		if (!visited.add(start.id())) {
			return;
		}
		List<Move> moves = cache.computeIfAbsent(start.slotId() + ":" + start.productId(), k -> jdbc.sql("""
			select id, finished_product_storage_slot_id as slot_id, finished_product_id as product_id, movement_type,
				quantity, transfer_reference, order_id, moved_at
			from finished_product_stock_movements
			where finished_product_storage_slot_id = :slot and finished_product_id = :product order by moved_at, id
			""").param("slot", start.slotId()).param("product", start.productId()).query(Move.class).list());
		int index = -1;
		long before = 0;
		for (int i = 0; i < moves.size(); i++) {
			if (moves.get(i).id() == start.id()) {
				index = i;
				break;
			}
			before += signed(moves.get(i));
		}
		if (index < 0) {
			return;
		}
		boolean certain = exact && before <= 0;
		long balance = Math.max(before, 0) + start.quantity();
		for (int i = index + 1; i < moves.size() && balance > 0; i++) {
			Move move = moves.get(i);
			switch (move.movementType()) {
				case "stock_in", "transfer_in", "order_return" -> certain = false;
				case "order_pick" -> {
					if (move.orderId() != null) {
						picks.add(new Pick(move.orderId(), move.productId(), move.quantity(), certain));
					}
				}
				case "transfer_out" -> {
					boolean carried = certain;
					counterpart(move).ifPresent(in -> walk(in, carried, cache, visited, picks));
				}
				default -> {
				}
			}
			balance += signed(move);
		}
	}

	/** Aktarmanin karsi raftaki giris hareketi (ayni referans). */
	private Optional<Move> counterpart(Move out) {
		if (out.transferReference() == null) {
			return Optional.empty();
		}
		return jdbc.sql("""
			select id, finished_product_storage_slot_id as slot_id, finished_product_id as product_id, movement_type,
				quantity, transfer_reference, order_id, moved_at
			from finished_product_stock_movements
			where transfer_reference = :ref and movement_type = 'transfer_in' and finished_product_id = :product
			order by id limit 1
			""").param("ref", out.transferReference()).param("product", out.productId()).query(Move.class).optional();
	}

	private static long signed(Move move) {
		return switch (move.movementType()) {
			case "stock_in", "transfer_in", "order_return" -> move.quantity();
			default -> -move.quantity();
		};
	}

	private static Entry earlier(Entry a, Entry b) {
		return b.movedAt() != null && (a.movedAt() == null || b.movedAt().isBefore(a.movedAt())) ? b : a;
	}

}
