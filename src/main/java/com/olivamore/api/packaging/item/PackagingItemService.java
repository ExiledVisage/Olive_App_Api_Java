package com.olivamore.api.packaging.item;

import com.olivamore.api.packaging.lot.PackagingLotService;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code PackagingItemService}: ambalaj kalemleri. Eldeki adet buradan yazilmaz; lotlarin kalan toplami. */
@Service
class PackagingItemService {

	private static final String COUNTS = "(select count(*) from packaging_lots where packaging_items.id = packaging_lots.packaging_item_id) as lots_count,"
		+ " (select count(*) from packaging_lots where packaging_items.id = packaging_lots.packaging_item_id and status = 'active') as active_lot_count";

	private final Db db;
	private final PackagingLotService lots;

	PackagingItemService(Db db, PackagingLotService lots) {
		this.db = db;
		this.lots = lots;
	}

	/** {@code getAll}: {@code isActive} verilirse suzulur; en yeni once ({@code latest()}); lot sayilari yuklu. */
	List<Row> all(Boolean isActive) {
		StringBuilder sql = new StringBuilder("select packaging_items.*, " + COUNTS + " from packaging_items");
		List<Object> args = new ArrayList<>();
		if (isActive != null) {
			sql.append(" where is_active = ?");
			args.add(isActive);
		}
		sql.append(" order by created_at desc");
		return db.select(sql.toString(), args.toArray());
	}

	/** {@code getById}: lot sayilari yuklu. */
	Row byId(long id) {
		return db.first("select packaging_items.*, " + COUNTS + " from packaging_items where id = ?", id).orElseThrow();
	}

	/** Yeni kalem. {@code current_stock} verilirse sutuna degil devir lotu olarak yazilir ({@code DEVIR-2026-0018}). @return yeni id */
	long create(Map<String, Object> validated, long userId) {
		Map<String, Object> data = new LinkedHashMap<>(validated);
		long opening = Php.intval(data.remove("current_stock"));
		if (data.get("is_active") == null) {
			data.put("is_active", true);
		}
		data.put("current_stock", 0);
		long id = db.insert("packaging_items", data);
		if (opening > 0) {
			lots.receive(id, String.format("DEVIR-%d-%04d", Timestamps.now().getYear(), id), opening, null, null,
				"Kalem tanımlanırken girilen devir stoğu.", userId);
		}
		return id;
	}

	/** Kalem kunyesi; eldeki adet buradan degismez. */
	void update(Row item, Map<String, Object> validated) {
		Map<String, Object> data = new LinkedHashMap<>(validated);
		data.remove("current_stock");
		db.update("packaging_items", item.id(), data);
	}

	void delete(Row item) {
		if (db.count("select count(*) from finished_product_components where packaging_item_id = ?", item.id()) > 0) {
			throw new BusinessRuleException("Bu ambalaj kalemi bir ürün bileşeninde kullanıldığı için silinemez.");
		}
		if (db.count("select count(*) from packaging_lots where packaging_item_id = ?", item.id()) > 0) {
			throw new BusinessRuleException("Bu ambalaj kaleminin lot kayıtları var; silinemez. Pasife alabilirsiniz.");
		}
		db.delete("packaging_items", item.id());
	}

}
