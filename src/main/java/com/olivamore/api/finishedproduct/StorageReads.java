package com.olivamore.api.finishedproduct;

import com.olivamore.api.shared.persistence.Casts;
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
 * Laravel {@code FinishedProductStorageResource}, {@code FinishedProductStorageSlotResource},
 * {@code FinishedProductStorageStockResource}, {@code FinishedProductStockMovementResource}.
 * Iliskiler Laravel'in eager loading sorgulariyla (siralamasiz, fiziksel sira).
 */
@Component
class StorageReads {

	/** {@code signedQuantity}: rafa giren hareketler +. */
	private static final Set<String> INCOMING = Set.of("stock_in", "transfer_in", "order_return");

	private final Db db;

	StorageReads(Db db) {
		this.db = db;
	}

	/** {@code STORAGE_RELATIONS} yuklu depo. */
	Map<String, Object> storage(long id) {
		return storages(List.of(db.find("finished_product_storages", id).orElseThrow())).getFirst();
	}

	/** {@code STORAGE_RELATIONS} yuklu depolar: raflar (urun kilidi ve icerigiyle), depo x urun stogu. */
	List<Map<String, Object>> storages(List<Row> storages) {
		List<Long> ids = Relations.ids(storages);
		List<Row> slotRows = db.whereIn("finished_product_storage_slots", "finished_product_storage_id", ids, null);
		List<Row> stockRows = db.whereIn("finished_product_storage_stocks", "finished_product_storage_id", ids, null);
		Map<Long, List<Map<String, Object>>> slots = new LinkedHashMap<>();
		List<Map<String, Object>> slotResources = slots(slotRows);
		for (int i = 0; i < slotRows.size(); i++) {
			slots.computeIfAbsent(slotRows.get(i).longValue("finished_product_storage_id"), k -> new ArrayList<>()).add(slotResources.get(i));
		}
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(stockRows, "finished_product_id"), null));
		Map<Long, List<Row>> stocks = Relations.groupBy(stockRows, "finished_product_storage_id");
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row s : storages) {
			Map<String, Object> r = base(s);
			r.put("slots", slots.getOrDefault(s.id(), List.of()));
			List<Map<String, Object>> stock = new ArrayList<>();
			for (Row st : stocks.getOrDefault(s.id(), List.of())) {
				Row product = products.get(st.longValue("finished_product_id"));
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", st.get("id"));
				if (product == null) {
					m.put("finished_product", null);
				}
				else {
					Map<String, Object> p = new LinkedHashMap<>();
					p.put("id", product.get("id"));
					p.put("name", product.get("name"));
					p.put("volume_litre", Php.floatval(Casts.decimal(product.get("volume_litre"), 4)));
					m.put("finished_product", p);
				}
				m.put("quantity", st.get("quantity"));
				m.put("updated_at", st.get("updated_at"));
				stock.add(m);
			}
			r.put("stock", stock);
			r.put("created_at", s.get("created_at"));
			r.put("updated_at", s.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** Iliski yuklenmemis depo (ekleme cevabi): {@code slots} ve {@code stock} yok. */
	static Map<String, Object> bare(Row s) {
		Map<String, Object> r = base(s);
		r.put("created_at", s.get("created_at"));
		r.put("updated_at", s.get("updated_at"));
		return r;
	}

	private static Map<String, Object> base(Row s) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", s.get("id"));
		r.put("name", s.get("name"));
		r.put("description", s.get("description"));
		r.put("is_active", Casts.bool(s.get("is_active")));
		return r;
	}

	/** Raflar: urun kilidi ve icerik ({@code stocks.finishedProduct}) yuklu. */
	List<Map<String, Object>> slots(List<Row> slots) {
		List<Row> stockRows = db.whereIn("finished_product_storage_slot_stocks", "finished_product_storage_slot_id", Relations.ids(slots), null);
		List<Long> productIds = new ArrayList<>(Relations.column(slots, "finished_product_id"));
		productIds.addAll(Relations.column(stockRows, "finished_product_id"));
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", productIds.stream().distinct().toList(), null));
		Map<Long, List<Row>> stocks = Relations.groupBy(stockRows, "finished_product_storage_slot_id");
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row slot : slots) {
			out.add(slot(slot, slot.get("volume_litre"), stocks.getOrDefault(slot.id(), List.of()), products));
		}
		return out;
	}

	/** FinishedProductStorageSlotResource. @param volume {@code (float) volume_litre} (ekleme cevabinda istekteki deger) */
	static Map<String, Object> slot(Row slot, Object volume, List<Row> stocks, Map<Long, Row> products) {
		long max = Php.intval(slot.get("max_quantity"));
		long current = Php.intval(slot.get("current_quantity"));
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", slot.get("id"));
		r.put("name", slot.get("name"));
		r.put("volume_litre", Php.floatval(volume));
		r.put("max_quantity", max);
		r.put("current_quantity", current);
		r.put("available", max - current);
		Row locked = slot.longValue("finished_product_id") == null ? null : products.get(slot.longValue("finished_product_id"));
		r.put("finished_product", locked == null ? null : idName(locked));
		List<Map<String, Object>> contents = new ArrayList<>();
		for (Row stock : stocks) {
			Row product = products.get(stock.longValue("finished_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", stock.get("finished_product_id"));
			p.put("name", product == null ? null : product.get("name"));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("finished_product", p);
			m.put("quantity", Php.intval(stock.get("quantity")));
			contents.add(m);
		}
		r.put("contents", contents);
		r.put("created_at", slot.get("created_at"));
		r.put("updated_at", slot.get("updated_at"));
		return r;
	}

	/** Deponun hareket gecmisi, yeniden eskiye: raf, karsi raf (deposuyla), urun, kullanici. */
	List<Map<String, Object>> movements(long storageId) {
		List<Row> rows = db.select("select * from finished_product_stock_movements where finished_product_storage_id = ?"
			+ " order by moved_at desc, id desc", storageId);
		Map<Long, Row> slots = Relations.byId(db.whereIn("finished_product_storage_slots", "id",
			Relations.column(rows, "finished_product_storage_slot_id"), null));
		Map<Long, Row> counterparties = Relations.byId(db.whereIn("finished_product_storage_slots", "id",
			Relations.column(rows, "counterparty_slot_id"), null));
		Map<Long, Row> counterpartyStorages = Relations.byId(db.whereIn("finished_product_storages", "id",
			Relations.column(List.copyOf(counterparties.values()), "finished_product_storage_id"), null));
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(rows, "finished_product_id"), null));
		Map<Long, Row> users = Relations.byId(db.whereIn("users", "id", Relations.column(rows, "created_by_user_id"), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row mv : rows) {
			long quantity = Php.intval(mv.get("quantity"));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", mv.get("id"));
			r.put("movement_type", mv.get("movement_type"));
			r.put("quantity", quantity);
			r.put("signed_quantity", INCOMING.contains(mv.string("movement_type")) ? quantity : -quantity);
			r.put("transfer_reference", mv.get("transfer_reference") == null ? null : mv.get("transfer_reference").toString());
			r.put("note", mv.get("note"));
			r.put("moved_at", mv.get("moved_at"));
			r.put("finished_product", idName(products.get(mv.longValue("finished_product_id"))));
			r.put("slot", idName(slots.get(mv.longValue("finished_product_storage_slot_id"))));
			Row counterparty = mv.longValue("counterparty_slot_id") == null ? null : counterparties.get(mv.longValue("counterparty_slot_id"));
			if (counterparty == null) {
				r.put("counterparty_slot", null);
			}
			else {
				Map<String, Object> c = idName(counterparty);
				Row storage = counterpartyStorages.get(counterparty.longValue("finished_product_storage_id"));
				c.put("storage_name", storage == null ? null : storage.get("name"));
				r.put("counterparty_slot", c);
			}
			Row user = mv.longValue("created_by_user_id") == null ? null : users.get(mv.longValue("created_by_user_id"));
			r.put("created_by_user", user == null ? null : idName(user));
			out.add(r);
		}
		return out;
	}

	private static Map<String, Object> idName(Row row) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", row.get("id"));
		m.put("name", row.get("name"));
		return m;
	}

}
