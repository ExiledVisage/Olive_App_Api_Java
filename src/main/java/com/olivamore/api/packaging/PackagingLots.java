package com.olivamore.api.packaging;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code PackagingLotService} (okuma, mal girisi, duzeltme, silme) ve {@code PackagingLotResource}.
 * Dolum kosusu tahsisi ve tuketimi Faz 8'de.
 */
@Component
class PackagingLots {

	/** {@code PackagingLotMovement::INCOMING} */
	private static final Set<String> INCOMING = Set.of("IN", "OPENING", "ADJUSTMENT_IN");

	private final Db db;

	PackagingLots(Db db) {
		this.db = db;
	}

	// ---- okuma ---------------------------------------------------------------

	/** {@code getAll}: kalem ve tedarikci yuklu, giris zamani ve id'ye gore yeniden eskiye. */
	List<Map<String, Object>> list(Long itemId, Long supplierId, String status, String search) {
		StringBuilder sql = new StringBuilder("select * from packaging_lots");
		List<String> where = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (itemId != null) {
			where.add("packaging_item_id = ?");
			args.add(itemId);
		}
		if (supplierId != null) {
			where.add("product_supplier_id = ?");
			args.add(supplierId);
		}
		if (status != null && !status.isEmpty() && !"0".equals(status)) {
			where.add("status = ?");
			args.add(status);
		}
		String term = search == null ? "" : search.trim();
		if (!term.isEmpty()) {
			String like = "%" + term + "%";
			where.add("(lot_code::text ilike ?"
				+ " or exists (select * from packaging_items where packaging_lots.packaging_item_id = packaging_items.id"
				+ " and name::text ilike ?)"
				+ " or exists (select * from product_suppliers where packaging_lots.product_supplier_id = product_suppliers.id"
				+ " and (name::text ilike ? or code::text ilike ?)))");
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (!where.isEmpty()) {
			sql.append(" where ").append(String.join(" and ", where));
		}
		sql.append(" order by received_at desc, id desc");
		return resources(db.select(sql.toString(), args.toArray()), true, true, false);
	}

	/** {@code getById}: kalem, tedarikci, hareketler (kosu ve urunuyle) ve bu lottan ceken kosular. */
	Map<String, Object> detail(long lotId) {
		return resources(List.of(db.find("packaging_lots", lotId).orElseThrow()), true, true, true).getFirst();
	}

	/**
	 * PackagingLotResource listesi. {@code item}/{@code supplier}: iliski yuklu mu;
	 * {@code details}: hareketler ve kosular da (detay).
	 */
	List<Map<String, Object>> resources(List<Row> lots, boolean item, boolean supplier, boolean details) {
		Map<Long, Row> items = item
			? Relations.byId(db.whereIn("packaging_items", "id", Relations.column(lots, "packaging_item_id"), null))
			: Map.of();
		Map<Long, Row> suppliers = supplier
			? Relations.byId(db.whereIn("product_suppliers", "id", Relations.column(lots, "product_supplier_id"), null))
			: Map.of();
		Map<Long, List<Row>> movements = Map.of();
		Map<Long, List<Row>> runPackagings = Map.of();
		Map<Long, Row> runs = Map.of();
		Map<Long, Row> products = Map.of();
		if (details && !lots.isEmpty()) {
			List<Row> movementRows = db.select("select * from packaging_lot_movements where packaging_lot_id in ("
				+ Relations.inList(Relations.ids(lots)) + ") order by moved_at asc, id asc");
			List<Row> packagingRows = db.whereIn("finished_product_run_packagings", "packaging_lot_id", Relations.ids(lots), null);
			List<Long> runIds = new ArrayList<>(Relations.column(movementRows, "finished_product_run_id"));
			runIds.addAll(Relations.column(packagingRows, "finished_product_run_id"));
			runs = Relations.byId(db.whereIn("finished_product_runs", "id", runIds.stream().distinct().toList(), null));
			products = Relations.byId(db.whereIn("finished_products", "id",
				Relations.column(List.copyOf(runs.values()), "finished_product_id"), null));
			movements = Relations.groupBy(movementRows, "packaging_lot_id");
			runPackagings = Relations.groupBy(packagingRows, "packaging_lot_id");
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row lot : lots) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", lot.get("id"));
			r.put("lot_code", lot.get("lot_code"));
			r.put("received_at", lot.get("received_at"));
			int received = toInt(lot.get("quantity_received"));
			int remaining = toInt(lot.get("quantity_remaining"));
			r.put("quantity_received", received);
			r.put("quantity_remaining", remaining);
			r.put("quantity_used", received - remaining);
			r.put("status", lot.get("status"));
			r.put("note", lot.get("note"));
			if (item) {
				Row i = items.get(lot.longValue("packaging_item_id"));
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", i.get("id"));
				m.put("name", i.get("name"));
				m.put("type", i.get("type"));
				r.put("packaging_item", m);
			}
			if (supplier) {
				Row s = lot.longValue("product_supplier_id") == null ? null : suppliers.get(lot.longValue("product_supplier_id"));
				if (s == null) {
					r.put("product_supplier", null);
				}
				else {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("id", s.get("id"));
					m.put("name", s.get("name"));
					m.put("code", s.get("code"));
					r.put("product_supplier", m);
				}
			}
			if (details) {
				List<Map<String, Object>> moves = new ArrayList<>();
				for (Row mv : movements.getOrDefault(lot.id(), List.of())) {
					moves.add(movement(mv, runs, products));
				}
				r.put("movements", moves);
				List<Map<String, Object>> lotRuns = new ArrayList<>();
				for (Row p : runPackagings.getOrDefault(lot.id(), List.of())) {
					Row run = runs.get(p.longValue("finished_product_run_id"));
					if (run == null) {
						continue;
					}
					Row product = products.get(run.longValue("finished_product_id"));
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("id", run.get("id"));
					m.put("status", run.get("status"));
					m.put("quantity", toInt(p.get("quantity")));
					m.put("finished_product_name", product == null ? null : product.get("name"));
					m.put("bottle_quantity", run.get("quantity"));
					m.put("completed_at", run.get("completed_at"));
					lotRuns.add(m);
				}
				r.put("runs", lotRuns);
			}
			r.put("created_at", lot.get("created_at"));
			r.put("updated_at", lot.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** PackagingLotMovementResource ({@code run} yuklu). */
	private static Map<String, Object> movement(Row mv, Map<Long, Row> runs, Map<Long, Row> products) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", mv.get("id"));
		r.put("movement_type", mv.get("movement_type"));
		r.put("source_type", mv.get("source_type"));
		r.put("quantity", toInt(mv.get("quantity")));
		r.put("is_incoming", INCOMING.contains(mv.string("movement_type")));
		r.put("note", mv.get("note"));
		r.put("moved_at", mv.get("moved_at"));
		r.put("finished_product_run_id", mv.get("finished_product_run_id"));
		Row run = mv.longValue("finished_product_run_id") == null ? null : runs.get(mv.longValue("finished_product_run_id"));
		Row product = run == null || run.longValue("finished_product_id") == null ? null : products.get(run.longValue("finished_product_id"));
		r.put("finished_product_name", product == null ? null : product.get("name"));
		r.put("created_at", mv.get("created_at"));
		return r;
	}

	// ---- mal girisi ve duzeltme ---------------------------------------------

	/**
	 * {@code receive}: yeni lot, giris hareketi, kalemin eldeki adedi.
	 * @param receivedAt dogrulanmis {@code received_at} (Carbon::parse); null ise simdi
	 * @return yeni lot id
	 */
	long receive(long itemId, String lotCode, long quantity, LocalDateTime receivedAt, Object supplierId, Object note, long userId) {
		LocalDateTime at = receivedAt == null ? Timestamps.now() : receivedAt;
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("packaging_item_id", itemId);
		values.put("product_supplier_id", supplierId);
		values.put("lot_code", lotCode);
		values.put("received_at", at);
		values.put("quantity_received", quantity);
		values.put("quantity_remaining", quantity);
		values.put("status", "active");
		values.put("note", note);
		long lotId = db.insert("packaging_lots", values);
		recordMovement(lotId, itemId, "IN", "SUPPLIER_DELIVERY", quantity, note, at, userId);
		recalculateItemStock(itemId);
		return lotId;
	}

	/**
	 * {@code adjust}: sayim farki, fire, iade. Lot kilitlenir; kalanin altina inilemez; fire ve iade yalnizca
	 * dusus.
	 */
	void adjust(long lotId, long delta, String reason, Object note, long userId) {
		if (delta == 0) {
			throw new BusinessRuleException("Düzeltme miktarı sıfır olamaz.");
		}
		Row lot = db.first("select * from packaging_lots where id = ? for update", lotId)
			.orElseThrow(() -> new ModelNotFoundException("PackagingLot", lotId));
		long remaining = toInt(lot.get("quantity_remaining"));
		if (delta < 0 && remaining + delta < 0) {
			throw new BusinessRuleException("'" + lot.string("lot_code") + "' lotunda " + remaining + " adet kaldı; "
				+ Math.abs(delta) + " adet düşülemez.");
		}
		String why = reason == null ? "COUNT" : reason;
		if (("SCRAP".equals(why) || "RETURN".equals(why)) && delta > 0) {
			throw new BusinessRuleException("Fire ve iade stoğu artırmaz; sayım fazlası için sebebi \"sayım farkı\" seçin.");
		}
		String type = switch (why) {
			case "SCRAP" -> "SCRAP";
			case "RETURN" -> "RETURN";
			default -> delta > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT";
		};
		long left = remaining + delta;
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("quantity_remaining", left);
		changes.put("status", left == 0 ? "depleted" : "active");
		db.update("packaging_lots", lotId, changes);
		long itemId = lot.longValue("packaging_item_id");
		recordMovement(lotId, itemId, type, "MANUAL_ADJUSTMENT", Math.abs(delta), note, Timestamps.now(), userId);
		recalculateItemStock(itemId);
	}

	/** {@code delete}: yalnizca hic kullanilmamis lot; hareketleriyle silinir. */
	void delete(Row lot) {
		String code = lot.string("lot_code");
		if (toInt(lot.get("quantity_remaining")) != toInt(lot.get("quantity_received"))) {
			throw new BusinessRuleException("'" + code + "' lotundan çıkış yapılmış, silinemez. "
				+ "Kalan miktarı sayım düzeltmesiyle sıfırlayabilirsiniz.");
		}
		if (db.count("select count(*) from finished_product_run_packagings where packaging_lot_id = ?", lot.id()) > 0) {
			throw new BusinessRuleException("'" + code + "' lotu bir dolum koşusuna tahsis edilmiş, silinemez.");
		}
		db.execute("delete from packaging_lot_movements where packaging_lot_id = ?", lot.id());
		db.delete("packaging_lots", lot.id());
		recalculateItemStock(lot.longValue("packaging_item_id"));
	}

	/** {@code recalculateItemStock}: kalemin eldeki adedi = lotlarin kalani. */
	void recalculateItemStock(long itemId) {
		long total = db.count("select coalesce(sum(quantity_remaining), 0) from packaging_lots where packaging_item_id = ?", itemId);
		db.update("packaging_items", itemId, Map.of("current_stock", total));
	}

	private void recordMovement(long lotId, long itemId, String type, String source, long quantity, Object note,
		LocalDateTime movedAt, long userId) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("packaging_lot_id", lotId);
		values.put("packaging_item_id", itemId);
		values.put("finished_product_run_id", null);
		values.put("user_id", userId);
		values.put("source_type", source);
		values.put("movement_type", type);
		values.put("quantity", quantity);
		values.put("note", note);
		values.put("moved_at", movedAt);
		db.insert("packaging_lot_movements", values);
	}

	/** integer cast'i */
	private static int toInt(Object value) {
		return (int) Php.intval(value);
	}

}
