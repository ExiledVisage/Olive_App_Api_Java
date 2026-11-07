package com.olivamore.api.finishedproduct;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Laravel {@code FinishedProductStorageService}: raf kurallari, depoya yerlestirme, raftan rafa transfer, cikis.
 * Dort sayim birlikte guncellenir: urunun {@code total_stock}'u, depo x urun, raf x urun, raf toplami.
 */
@Component
class StorageFlow {

	private static final double VOLUME_EPSILON = 0.0001;

	private final Db db;

	StorageFlow(Db db) {
		this.db = db;
	}

	// ---- raf -------------------------------------------------------------------

	/** {@code createSlot}: kilitli urun verilirse hacmi rafinkiyle ayni olmali. @return yeni raf */
	Row createSlot(long storageId, Map<String, Object> data) {
		assertProductFitsSlot(Php.floatval(data.get("volume_litre")), data.get("finished_product_id"));
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", data.get("name"));
		values.put("volume_litre", data.get("volume_litre"));
		values.put("max_quantity", data.get("max_quantity"));
		values.put("current_quantity", 0);
		values.put("finished_product_id", data.get("finished_product_id"));
		values.put("finished_product_storage_id", storageId);
		return db.create("finished_product_storage_slots", values);
	}

	/**
	 * {@code updateSlot}: kapasite kullanimin altina inemez, dolu rafin hacmi degismez, baska urun varken tek
	 * urune kilitlenemez; null gonderilerek kilit kaldirilir.
	 */
	void updateSlot(long slotId, Map<String, Object> data) {
		Row slot = lockSlot(slotId);
		List<Row> stocks = db.whereIn("finished_product_storage_slot_stocks", "finished_product_storage_slot_id", List.of(slotId), null);
		long current = Php.intval(slot.get("current_quantity"));
		if (data.containsKey("max_quantity") && Php.intval(data.get("max_quantity")) < current) {
			throw new BusinessRuleException("Yeni max_quantity (" + Php.strval(data.get("max_quantity")) + ") mevcut kullanımın ("
				+ current + ") altına düşemez.");
		}
		boolean volumeChanges = data.containsKey("volume_litre")
			&& Math.abs(Php.floatval(data.get("volume_litre")) - Php.floatval(Casts.decimal(slot.get("volume_litre"), 4))) > VOLUME_EPSILON;
		if (volumeChanges && current > 0) {
			throw new BusinessRuleException("Slot " + label(slot) + " içinde " + current + " şişe varken hacmi değiştirilemez.");
		}
		double effectiveVolume = data.containsKey("volume_litre") ? Php.floatval(data.get("volume_litre"))
			: Php.floatval(Casts.decimal(slot.get("volume_litre"), 4));
		Object effectiveLock = data.containsKey("finished_product_id") ? data.get("finished_product_id") : slot.get("finished_product_id");
		if (effectiveLock != null) {
			long lockId = Php.intval(effectiveLock);
			List<String> names = new ArrayList<>();
			boolean others = false;
			for (Row stock : stocks) {
				if (Php.intval(stock.get("finished_product_id")) != lockId && Php.intval(stock.get("quantity")) > 0) {
					others = true;
					db.find("finished_products", stock.longValue("finished_product_id"))
						.map(p -> p.string("name")).filter(n -> !n.isEmpty()).ifPresent(names::add);
				}
			}
			if (others) {
				throw new BusinessRuleException("Slot " + label(slot) + " içinde başka ürünler var (" + String.join(", ", names)
					+ "); tek ürüne kilitlenemez.");
			}
			assertProductFitsSlot(effectiveVolume, lockId);
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		for (String key : List.of("name", "volume_litre", "max_quantity", "finished_product_id")) {
			if (data.containsKey(key)) {
				payload.put(key, key.equals("volume_litre") ? Casts.decimal(data.get(key), 4) : data.get(key));
			}
		}
		if (!payload.isEmpty()) {
			db.update("finished_product_storage_slots", slotId, payload);
		}
	}

	// ---- depoya yerlestirme -------------------------------------------------------

	/**
	 * {@code stockIn}: tek raf ya da {@code placements} ile birden cok raf, tek islemde. Acikta yeterli sise
	 * olup olmadigi raflara girmeden once toplam uzerinden bakilir.
	 */
	void stockIn(Row storage, Map<String, Object> data, long actorId) {
		Row product = lockProduct(Php.intval(data.get("finished_product_id")));
		List<long[]> placements = new ArrayList<>();
		if (data.get("placements") != null) {
			for (Map<String, Object> p : RecipeController.items(data.get("placements"))) {
				placements.add(new long[] {Php.intval(p.get("finished_product_storage_slot_id")), Php.intval(p.get("quantity"))});
			}
		}
		else {
			placements.add(new long[] {Php.intval(data.get("finished_product_storage_slot_id")), Php.intval(data.get("quantity"))});
		}
		assertStorageActive(storage);
		Set<Long> seen = new HashSet<>();
		for (long[] p : placements) {
			if (!seen.add(p[0])) {
				throw new BusinessRuleException("Aynı raf listede birden fazla kez verilemez.");
			}
		}
		long total = placements.stream().mapToLong(p -> p[1]).sum();
		assertUnstoredAvailable(product, total);
		Long runId = data.get("finished_product_run_id") == null ? null : Php.intval(data.get("finished_product_run_id"));
		for (long[] p : placements) {
			Row slot = lockSlot(p[0]);
			assertSlotBelongsToStorage(slot, storage);
			assertSlotAccepts(slot, product, p[1]);
			addToSlot(slot, product, p[1]);
			recordMovement(storage.id(), slot.id(), product.id(), "stock_in", p[1], actorId, data.get("note"), null, null, runId, null);
		}
	}

	/** {@code transfer}: raftan rafa (ayni ya da baska depo); raflar id sirasiyla kilitlenir. @return hedef depo id */
	long transfer(Row sourceStorage, Map<String, Object> data, long actorId) {
		long sourceSlotId = Php.intval(data.get("source_slot_id"));
		long targetSlotId = Php.intval(data.get("target_slot_id"));
		long qty = Php.intval(data.get("quantity"));
		if (sourceSlotId == targetSlotId) {
			throw new BusinessRuleException("Kaynak ve hedef slot aynı olamaz.");
		}
		Map<Long, Row> locked = new LinkedHashMap<>();
		for (long id : new long[] {Math.min(sourceSlotId, targetSlotId), Math.max(sourceSlotId, targetSlotId)}) {
			locked.put(id, lockSlot(id));
		}
		Row sourceSlot = locked.get(sourceSlotId);
		Row targetSlot = locked.get(targetSlotId);
		long productId = Php.intval(data.get("finished_product_id"));
		Row product = db.find("finished_products", productId).orElseThrow(() -> new ModelNotFoundException("FinishedProduct", productId));
		assertSlotBelongsToStorage(sourceSlot, sourceStorage);
		long targetStorageId = targetSlot.longValue("finished_product_storage_id");
		Row targetStorage = db.find("finished_product_storages", targetStorageId)
			.orElseThrow(() -> new ModelNotFoundException("FinishedProductStorage", targetStorageId));
		assertStorageActive(targetStorage);
		assertSlotAccepts(targetSlot, product, qty);
		removeFromSlot(sourceSlot, product, qty);
		addToSlot(targetSlot, product, qty);
		String reference = UUID.randomUUID().toString();
		recordMovement(sourceStorage.id(), sourceSlot.id(), product.id(), "transfer_out", qty, actorId, null, targetSlot.id(), reference, null, null);
		recordMovement(targetStorage.id(), targetSlot.id(), product.id(), "transfer_in", qty, actorId, null, sourceSlot.id(), reference, null, null);
		return targetStorage.id();
	}

	/** {@code stockOut}: sise fabrikadan cikar; raftan ve {@code total_stock}'tan duser. */
	void stockOut(Row storage, Map<String, Object> data, long actorId) {
		Row slot = lockSlot(Php.intval(data.get("finished_product_storage_slot_id")));
		Row product = lockProduct(Php.intval(data.get("finished_product_id")));
		long qty = Php.intval(data.get("quantity"));
		assertSlotBelongsToStorage(slot, storage);
		removeFromSlot(slot, product, qty);
		if (Php.intval(product.get("total_stock")) < qty) {
			throw new BusinessRuleException("'" + product.string("name") + "' için total_stock (" + product.get("total_stock")
				+ ") depodaki stoktan az; kayıtlar tutarsız. Çıkış yapılamadı.");
		}
		db.execute("update finished_products set total_stock = total_stock - ?, updated_at = ? where id = ?", qty, Timestamps.now(), product.id());
		recordMovement(storage.id(), slot.id(), product.id(), "stock_out", qty, actorId, null, null, null, null, null);
	}

	// ---- siparis (Faz 9) ---------------------------------------------------------------

	/** Siparis icin raftan alinan: raf, urun, adet. */
	record Pick(long slotId, long productId, long quantity) {
	}

	/**
	 * {@code pickForOrder}: sise raftan cikar ama fabrikadan cikmaz ({@code total_stock} sevkiyatta duser).
	 * Rafta o kadar yoksa hata.
	 */
	void pickForOrder(long orderId, List<Pick> picks, Long actorId) {
		for (Pick pick : picks) {
			Row slot = lockSlot(pick.slotId());
			Row product = lockProduct(pick.productId());
			removeFromSlot(slot, product, pick.quantity());
			recordMovement(slot.longValue("finished_product_storage_id"), slot.id(), product.id(), "order_pick", pick.quantity(), actorId,
				"Siparis #" + orderId + " icin raftan alindi.", null, null, null, orderId);
		}
	}

	/**
	 * {@code returnOrderPicks}: iptal edilen hazir siparisin siseleri alindiklari raflara; daha once geri konan
	 * dusulur, raf kapasitesi yeniden kontrol edilir.
	 */
	void returnOrderPicks(long orderId, Long actorId) {
		List<Row> picks = db.select("select * from finished_product_stock_movements where order_id = ? and movement_type = ?",
			orderId, "order_pick");
		Map<String, Long> returned = new LinkedHashMap<>();
		for (Row r : db.select("select * from finished_product_stock_movements where order_id = ? and movement_type = ?",
			orderId, "order_return")) {
			returned.merge(r.get("finished_product_storage_slot_id") + ":" + r.get("finished_product_id"), Php.intval(r.get("quantity")), Long::sum);
		}
		for (Row pick : picks) {
			String key = pick.get("finished_product_storage_slot_id") + ":" + pick.get("finished_product_id");
			long remaining = Php.intval(pick.get("quantity")) - returned.getOrDefault(key, 0L);
			if (remaining <= 0) {
				continue;
			}
			Row slot = lockSlot(pick.longValue("finished_product_storage_slot_id"));
			Row product = lockProduct(pick.longValue("finished_product_id"));
			assertSlotAccepts(slot, product, remaining);
			addToSlot(slot, product, remaining);
			recordMovement(slot.longValue("finished_product_storage_id"), slot.id(), product.id(), "order_return", remaining, actorId,
				"Siparis #" + orderId + " iptal edildi, rafa geri konuldu.", null, null, null, orderId);
		}
	}

	// ---- sayim yardimcilari ----------------------------------------------------------

	private void addToSlot(Row slot, Row product, long qty) {
		increment("finished_product_storage_slots", slot.id(), "current_quantity", qty);
		Row slotStock = firstOrCreate("finished_product_storage_slot_stocks", "finished_product_storage_slot_id", slot.id(), product.id());
		increment("finished_product_storage_slot_stocks", slotStock.id(), "quantity", qty);
		Row storageStock = firstOrCreate("finished_product_storage_stocks", "finished_product_storage_id",
			slot.longValue("finished_product_storage_id"), product.id());
		increment("finished_product_storage_stocks", storageStock.id(), "quantity", qty);
	}

	private void removeFromSlot(Row slot, Row product, long qty) {
		Row slotStock = db.first("select * from finished_product_storage_slot_stocks where finished_product_storage_slot_id = ?"
			+ " and finished_product_id = ? limit 1 for update", slot.id(), product.id()).orElse(null);
		long inSlot = slotStock == null ? 0 : Php.intval(slotStock.get("quantity"));
		if (inSlot < qty) {
			throw new BusinessRuleException("Slot " + label(slot) + " içinde '" + product.string("name") + "' yetersiz. "
				+ "Slotta: " + inSlot + ", istenen: " + qty + ".");
		}
		Row storageStock = db.first("select * from finished_product_storage_stocks where finished_product_storage_id = ?"
			+ " and finished_product_id = ? limit 1 for update", slot.longValue("finished_product_storage_id"), product.id()).orElse(null);
		if (storageStock == null || Php.intval(storageStock.get("quantity")) < qty) {
			throw new BusinessRuleException("Depo stok kaydı slot içeriğiyle tutarsız ('" + product.string("name") + "'). İşlem yapılamadı.");
		}
		increment("finished_product_storage_slots", slot.id(), "current_quantity", -qty);
		if (inSlot == qty) {
			db.delete("finished_product_storage_slot_stocks", slotStock.id());
		}
		else {
			increment("finished_product_storage_slot_stocks", slotStock.id(), "quantity", -qty);
		}
		increment("finished_product_storage_stocks", storageStock.id(), "quantity", -qty);
	}

	/** {@code $model->increment($column, $amount)}: updated_at da yazilir. */
	private void increment(String table, long id, String column, long amount) {
		db.execute("update " + table + " set " + column + " = " + column + " + ?, updated_at = ? where id = ?", amount, Timestamps.now(), id);
	}

	/** {@code firstOrCreate([...], ['quantity' => 0])} */
	private Row firstOrCreate(String table, String parentColumn, long parentId, long productId) {
		return db.first("select * from " + table + " where " + parentColumn + " = ? and finished_product_id = ? limit 1", parentId, productId)
			.orElseGet(() -> {
				Map<String, Object> values = new LinkedHashMap<>();
				values.put(parentColumn, parentId);
				values.put("finished_product_id", productId);
				values.put("quantity", 0);
				return db.create(table, values);
			});
	}

	private void recordMovement(long storageId, long slotId, long productId, String type, long quantity, Long actorId, Object note,
		Long counterpartySlotId, String reference, Long runId, Long orderId) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("finished_product_storage_id", storageId);
		values.put("finished_product_storage_slot_id", slotId);
		values.put("finished_product_id", productId);
		values.put("finished_product_run_id", runId);
		values.put("order_id", orderId);
		values.put("movement_type", type);
		values.put("quantity", quantity);
		values.put("counterparty_slot_id", counterpartySlotId);
		values.put("transfer_reference", reference);
		values.put("created_by_user_id", actorId);
		values.put("note", note);
		values.put("moved_at", Timestamps.now());
		db.insert("finished_product_stock_movements", values);
	}

	// ---- kontroller --------------------------------------------------------------------

	private Row lockSlot(long slotId) {
		return db.first("select * from finished_product_storage_slots where id = ? limit 1 for update", slotId)
			.orElseThrow(() -> new ModelNotFoundException("FinishedProductStorageSlot", slotId));
	}

	private Row lockProduct(long productId) {
		return db.first("select * from finished_products where id = ? limit 1 for update", productId)
			.orElseThrow(() -> new ModelNotFoundException("FinishedProduct", productId));
	}

	private static String label(Row slot) {
		String name = slot.string("name");
		return name != null && !name.isEmpty() && !"0".equals(name) ? "'" + name + "'" : "#" + slot.id();
	}

	private static void assertSlotBelongsToStorage(Row slot, Row storage) {
		if (!Objects.equals(slot.longValue("finished_product_storage_id"), storage.id())) {
			throw new BusinessRuleException("Slot " + label(slot) + " '" + storage.string("name") + "' deposuna ait değil.");
		}
	}

	private static void assertStorageActive(Row storage) {
		if (!Boolean.TRUE.equals(Casts.bool(storage.get("is_active")))) {
			throw new BusinessRuleException("'" + storage.string("name") + "' deposu pasif; ürün girişi yapılamaz.");
		}
	}

	private static void assertSlotAccepts(Row slot, Row product, long qty) {
		String productVolume = Casts.decimal(product.get("volume_litre"), 4);
		String slotVolume = Casts.decimal(slot.get("volume_litre"), 4);
		if (Math.abs(Php.floatval(slotVolume) - Php.floatval(productVolume)) > VOLUME_EPSILON) {
			throw new BusinessRuleException("Ürün hacmi (" + productVolume + "L) slot " + label(slot) + " hacmiyle (" + slotVolume
				+ "L) eşleşmiyor.");
		}
		Long locked = slot.longValue("finished_product_id");
		if (locked != null && locked != 0 && locked != product.id()) {
			throw new BusinessRuleException("Slot " + label(slot) + " farklı bir ürüne ayrılmış.");
		}
		long available = Php.intval(slot.get("max_quantity")) - Php.intval(slot.get("current_quantity"));
		if (qty > available) {
			throw new BusinessRuleException("Slot " + label(slot) + " kapasitesi yetersiz. Boş kapasite: " + available + ", istenen: " + qty + ".");
		}
	}

	/** Depoya yalnizca hicbir depoya yerlesmemis (ve sevkiyat beklemeyen) sise girebilir. */
	private void assertUnstoredAvailable(Row product, long qty) {
		long stored = db.count("select coalesce(sum(quantity), 0) from finished_product_storage_stocks where finished_product_id = ?", product.id());
		long awaiting = db.count("select coalesce(sum(quantity), 0) from finished_product_stock_movements where finished_product_id = ?"
			+ " and movement_type = ? and exists (select * from orders where finished_product_stock_movements.order_id = orders.id"
			+ " and status = ?)", product.id(), "order_pick", "ready");
		long total = Php.intval(product.get("total_stock"));
		long unstored = total - stored - awaiting;
		if (qty > unstored) {
			throw new BusinessRuleException("'" + product.string("name") + "' için depoya yerleştirilecek yeterli şişe yok. "
				+ "Fabrikada: " + total + ", depolarda: " + stored + ", sevkiyat bekleyen: " + awaiting + ", açıkta: " + Math.max(0, unstored)
				+ ", istenen: " + qty + ". Depolar arası taşıma için transfer kullanın.");
		}
	}

	/** Kilitlenecek urunun hacmi rafinkiyle ayni olmali (urun yoksa ya da id 0 ise kontrol yok). */
	private void assertProductFitsSlot(double volume, Object productId) {
		if (productId == null || Php.intval(productId) == 0) {
			return;
		}
		db.find("finished_products", Php.intval(productId)).ifPresent(product -> {
			String productVolume = Casts.decimal(product.get("volume_litre"), 4);
			if (Math.abs(Php.floatval(productVolume) - volume) > VOLUME_EPSILON) {
				throw new BusinessRuleException("Ürün hacmi (" + productVolume + "L) slot hacmiyle (" + Php.floatString(volume) + "L) eşleşmiyor.");
			}
		});
	}

}
