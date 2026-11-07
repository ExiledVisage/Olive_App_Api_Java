package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code PackagingLotService}: lot listesi, mal girisi, sayim duzeltmesi, silme, hareket defteri ve kalemin
 * eldeki adedi. Tek gercek kaynak lotlarin kalani; {@code packaging_items.current_stock} onbellek. Dolum kosusu
 * tahsisi {@code PackagingAllocations}'ta (modulun disa acik kapisi).
 */
@Service
public class PackagingLotService {

	private final Db db;

	PackagingLotService(Db db) {
		this.db = db;
	}

	/** Kunye duzeltmesi: lot numarasi, tedarikci, giris zamani, not. Miktar yalnizca hareketle degisir. */
	void update(Row lot, Map<String, Object> data) {
		db.update("packaging_lots", lot.id(), data);
	}

	/** {@code getAll}: kalem ve tedarikci yuklu, giris zamani ve id'ye gore yeniden eskiye. */
	List<Row> list(Long itemId, Long supplierId, String status, String search) {
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
		return db.select(sql.toString(), args.toArray());
	}

	/**
	 * {@code receive}: yeni lot, giris hareketi, kalemin eldeki adedi.
	 * @param receivedAt dogrulanmis {@code received_at} (Carbon::parse); null ise simdi
	 * @return yeni lot id
	 */
	public long receive(long itemId, String lotCode, long quantity, LocalDateTime receivedAt, Object supplierId, Object note, long userId) {
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
		recordMovement(lotId, itemId, "IN", "SUPPLIER_DELIVERY", quantity, note, at, userId, null);
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
		recordMovement(lotId, itemId, type, "MANUAL_ADJUSTMENT", Math.abs(delta), note, Timestamps.now(), userId, null);
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
	public void recalculateItemStock(long itemId) {
		long total = db.count("select coalesce(sum(quantity_remaining), 0) from packaging_lots where packaging_item_id = ?", itemId);
		db.update("packaging_items", itemId, Map.of("current_stock", total));
	}

	public void recordMovement(long lotId, long itemId, String type, String source, long quantity, Object note,
		LocalDateTime movedAt, long userId, Long runId) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("packaging_lot_id", lotId);
		values.put("packaging_item_id", itemId);
		values.put("finished_product_run_id", runId);
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
