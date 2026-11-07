package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code PackagingLotResource} ve {@code PackagingLotMovementResource}: lot, kalemi, tedarikcisi,
 * hareketleri ve bu lottan ceken dolum kosulari. Urun tedarikcisi de kullanir.
 */
@Component
public class PackagingLotResource {

	/** {@code PackagingLotMovement::INCOMING} */
	private static final Set<String> INCOMING = Set.of("IN", "OPENING", "ADJUSTMENT_IN");

	private final Db db;

	PackagingLotResource(Db db) {
		this.db = db;
	}

	/** {@code getById}: kalem, tedarikci, hareketler (kosu ve urunuyle) ve bu lottan ceken kosular. */
	Map<String, Object> detail(long lotId) {
		return of(List.of(db.find("packaging_lots", lotId).orElseThrow()), true, true, true).getFirst();
	}

	/**
	 * PackagingLotResource listesi. {@code item}/{@code supplier}: iliski yuklu mu;
	 * {@code details}: hareketler ve kosular da (detay).
	 */
	public List<Map<String, Object>> of(List<Row> lots, boolean item, boolean supplier, boolean details) {
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

	/** integer cast'i */
	private static int toInt(Object value) {
		return (int) Php.intval(value);
	}

}
