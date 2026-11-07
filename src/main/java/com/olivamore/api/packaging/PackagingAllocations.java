package com.olivamore.api.packaging;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code PackagingLotService}'in dolum kosusu kismi: FIFO tahsis, elle tahsis, tamamlamada tuketim.
 * Bitmis urun modulu cagirir. Tahsis bir rezervasyon degil; tamamlamada bir daha bakilir.
 */
@Component
public class PackagingAllocations {

	/** Dolum kosusunun lot tahsisi bu durumlarda degistirilebilir. */
	private static final Set<String> EDITABLE_RUN_STATUSES = Set.of("planned", "in_progress");

	private final Db db;
	private final PackagingLots lots;

	PackagingAllocations(Db db, PackagingLots lots) {
		this.db = db;
		this.lots = lots;
	}

	/** Kosunun ihtiyaci: urunun ambalaj bilesenleri (fiziksel sira), kalem basina adet x bilesen adedi. */
	public record Need(long itemId, long quantity) {
	}

	/**
	 * {@code allocateForRun}: eski tahsis silinir, her kalem en eski girisli lottan baslayarak tahsis edilir.
	 * Yetersiz stokta hata.
	 */
	public void allocateForRun(long runId, List<Need> needs) {
		db.execute("delete from finished_product_run_packagings where finished_product_run_id = ?", runId);
		for (Need need : needs) {
			allocateItem(runId, need.itemId(), need.quantity());
		}
	}

	private void allocateItem(long runId, long itemId, long needed) {
		if (needed <= 0) {
			return;
		}
		List<Row> active = db.select("select * from packaging_lots where packaging_item_id = ? and status = ? and quantity_remaining > ?"
			+ " order by received_at asc, id asc", itemId, "active", 0);
		long available = active.stream().mapToLong(l -> Php.intval(l.get("quantity_remaining"))).sum();
		if (available < needed) {
			throw new BusinessRuleException(itemName(itemId) + " için " + needed + " adet gerekiyor, lotlarda " + available
				+ " adet var. Önce mal girişi yapın.");
		}
		long remaining = needed;
		for (Row lot : active) {
			if (remaining == 0) {
				break;
			}
			long take = Math.min(remaining, Php.intval(lot.get("quantity_remaining")));
			insert(runId, itemId, lot.id(), take);
			remaining -= take;
		}
	}

	/**
	 * {@code syncRunAllocation}: gonderilen liste tahsisin tamami; kalem basina toplam gereken adede esit olmali.
	 * @param rows dogrulanmis {@code packagings} ({@code packaging_lot_id}, {@code quantity})
	 */
	public void syncRunAllocation(long runId, String runStatus, List<Need> needs, List<Map<String, Object>> rows) {
		if (!EDITABLE_RUN_STATUSES.contains(runStatus)) {
			throw new BusinessRuleException("'" + runStatus + "' durumundaki koşunun ambalaj lotları değiştirilemez.");
		}
		Map<Long, Long> needed = new LinkedHashMap<>();
		for (Need need : needs) {
			needed.put(need.itemId(), need.quantity());
		}
		List<Long> lotIds = rows.stream().map(r -> Php.intval(r.get("packaging_lot_id"))).toList();
		Map<Long, Row> found = Relations.byId(db.whereIn("packaging_lots", "id", lotIds, null));
		Map<Long, Long> allocated = new LinkedHashMap<>();
		List<long[]> prepared = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			long lotId = Php.intval(row.get("packaging_lot_id"));
			long quantity = Php.intval(row.get("quantity"));
			Row lot = found.get(lotId);
			if (lot == null) {
				throw new BusinessRuleException("#" + lotId + " numaralı lot bulunamadı.");
			}
			long itemId = lot.longValue("packaging_item_id");
			if (!needed.containsKey(itemId)) {
				throw new BusinessRuleException("'" + lot.string("lot_code") + "' lotu (" + itemName(itemId) + ") bu ürünün "
					+ "ambalaj listesinde yok.");
			}
			long remaining = Php.intval(lot.get("quantity_remaining"));
			if (quantity > remaining) {
				throw new BusinessRuleException("'" + lot.string("lot_code") + "' lotunda " + remaining + " adet var, "
					+ quantity + " adet seçildi.");
			}
			allocated.merge(itemId, quantity, Long::sum);
			prepared.add(new long[] {itemId, lotId, quantity});
		}
		needed.forEach((itemId, required) -> {
			long given = allocated.getOrDefault(itemId, 0L);
			if (given != required) {
				throw new BusinessRuleException(itemNameOrId(itemId) + " için " + required + " adet gerekiyor, " + given + " adet seçildi.");
			}
		});
		db.execute("delete from finished_product_run_packagings where finished_product_run_id = ?", runId);
		for (long[] p : prepared) {
			insert(runId, p[0], p[1], p[2]);
		}
	}

	/**
	 * {@code consumeForRun}: tahsis yoksa FIFO ile kurulur, hala yeterli mi bakilir, lotlardan dusulur ve
	 * cikis hareketleri yazilir.
	 */
	public void consumeForRun(long runId, List<Need> needs, long userId) {
		if (db.count("select count(*) from finished_product_run_packagings where finished_product_run_id = ?", runId) == 0) {
			allocateForRun(runId, needs);
		}
		assertAllocationStillAvailable(runId, needs);
		Set<Long> touched = new LinkedHashSet<>();
		for (Row allocation : allocations(runId)) {
			Row lot = db.first("select * from packaging_lots where id = ? for update", allocation.longValue("packaging_lot_id")).orElseThrow();
			long quantity = Php.intval(allocation.get("quantity"));
			long left = Php.intval(lot.get("quantity_remaining")) - quantity;
			Map<String, Object> changes = new LinkedHashMap<>();
			changes.put("quantity_remaining", left);
			changes.put("status", left == 0 ? "depleted" : "active");
			db.update("packaging_lots", lot.id(), changes);
			long itemId = lot.longValue("packaging_item_id");
			lots.recordMovement(lot.id(), itemId, "OUT", "FINISHED_PRODUCT_RUN", quantity, "Dolum Run #" + runId,
				Timestamps.now(), userId, runId);
			touched.add(itemId);
		}
		touched.forEach(lots::recalculateItemStock);
	}

	/** {@code assertAllocationStillAvailable}: tahsisli lotlarda yeterli adet ve kalem basina tam tahsis. */
	public void assertAllocationStillAvailable(long runId, List<Need> needs) {
		List<Row> allocations = allocations(runId);
		Map<Long, Row> lotsById = Relations.byId(db.whereIn("packaging_lots", "id", Relations.column(allocations, "packaging_lot_id"), null));
		List<String> problems = new ArrayList<>();
		for (Row allocation : allocations) {
			Row lot = lotsById.get(allocation.longValue("packaging_lot_id"));
			long quantity = Php.intval(allocation.get("quantity"));
			if (lot == null) {
				problems.add("#" + allocation.get("packaging_lot_id") + " numaralı lot silinmiş");
				continue;
			}
			long remaining = Php.intval(lot.get("quantity_remaining"));
			if (remaining < quantity) {
				problems.add("'" + lot.string("lot_code") + "' lotunda " + remaining + " adet kaldı, " + quantity + " adet gerekiyor");
			}
		}
		for (Need need : needs) {
			long given = allocations.stream().filter(a -> a.longValue("packaging_item_id") == need.itemId())
				.mapToLong(a -> Php.intval(a.get("quantity"))).sum();
			if (given != need.quantity()) {
				problems.add(itemNameOrId(need.itemId()) + " için " + need.quantity() + " adet gerekiyor, " + given + " adet tahsisli");
			}
		}
		if (!problems.isEmpty()) {
			throw new BusinessRuleException("Ambalaj tahsisi tamamlanamıyor: " + String.join("; ", problems)
				+ ". Lot seçimini güncelleyip yeniden deneyin.");
		}
	}

	/** {@code availableQuantity}: kalemin aktif lotlarinin kalani (onbellege degil lotlara bakar). */
	public long availableQuantity(long itemId) {
		return db.count("select coalesce(sum(quantity_remaining), 0) from packaging_lots where packaging_item_id = ? and status = ?",
			itemId, "active");
	}

	private List<Row> allocations(long runId) {
		return db.select("select * from finished_product_run_packagings where finished_product_run_id = ?", runId);
	}

	private void insert(long runId, long itemId, long lotId, long quantity) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("packaging_item_id", itemId);
		values.put("packaging_lot_id", lotId);
		values.put("quantity", quantity);
		values.put("finished_product_run_id", runId);
		db.insert("finished_product_run_packagings", values);
	}

	private String itemName(long itemId) {
		return db.find("packaging_items", itemId).map(i -> i.string("name")).orElse(null);
	}

	/** {@code PackagingItem::find($id)?->name ?? "#$id"} */
	private String itemNameOrId(long itemId) {
		String name = itemName(itemId);
		return name != null ? name : "#" + itemId;
	}

}
