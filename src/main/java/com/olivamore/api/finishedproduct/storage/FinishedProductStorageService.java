package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.finishedproduct.product.FinishedProduct;
import com.olivamore.api.finishedproduct.product.FinishedProductRepository;
import com.olivamore.api.finishedproduct.run.FinishedProductRunRepository;
import com.olivamore.api.finishedproduct.support.Quantities;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.finishedproduct.FinishedProductDirectory;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Depo ve raf kurallari, depoya yerlestirme, raftan rafa transfer, cikis. Dort sayim birlikte guncellenir: urunun
 * {@code total_stock}'u, depo x urun, raf x urun, raf toplami. Siparis modulu ayni islemde JDBC kullandigi icin
 * raf islemleri hemen veritabanina yazilir.
 */
@Service
public class FinishedProductStorageService {

	private static final String SLOT_FIELD = "finished_product_storage_slot_id";

	private final FinishedProductStorageRepository storages;
	private final StorageSlotRepository slots;
	private final SlotStockRepository slotStocks;
	private final StorageStockRepository storageStocks;
	private final StockMovementRepository movements;
	private final FinishedProductRepository products;
	private final FinishedProductRunRepository runs;
	private final FinishedProductDirectory directory;

	FinishedProductStorageService(FinishedProductStorageRepository storages, StorageSlotRepository slots,
		SlotStockRepository slotStocks, StorageStockRepository storageStocks, StockMovementRepository movements,
		FinishedProductRepository products, FinishedProductRunRepository runs, FinishedProductDirectory directory) {
		this.storages = storages;
		this.slots = slots;
		this.slotStocks = slotStocks;
		this.storageStocks = storageStocks;
		this.movements = movements;
		this.products = products;
		this.runs = runs;
		this.directory = directory;
	}

	// ---- depo ------------------------------------------------------------------

	@Transactional
	long create(CreateStorageRequest request) {
		return storages.save(new FinishedProductStorage(request)).getId();
	}

	@Transactional
	void update(long id, UpdateStorageRequest request) {
		find(id).apply(request);
	}

	/** Stoklu depo silinmez; stogu bosalmis ama hareket gecmisi olan depo 422 (kullanildigi yerlerle). */
	@Transactional
	void delete(long id) {
		FinishedProductStorage storage = find(id);
		if (storageStocks.existsByStorageIdAndQuantityGreaterThan(id, 0)) {
			throw new BusinessRuleException("Bu depoda stok bulunduğu için silinemez.");
		}
		DeletionGuard.of("Bu depo")
			.usedBy("stok hareketi", movements.countByStorageId(id))
			.check("Pasife alabilirsiniz.");
		storages.delete(storage);
	}

	// ---- raf -------------------------------------------------------------------

	/** Kilitli urun verilirse hacmi rafinkiyle ayni olmali. */
	@Transactional
	long createSlot(long storageId, CreateSlotRequest request) {
		find(storageId);
		if (request.finishedProductId() != null) {
			assertProductExists(request.finishedProductId());
			assertProductFitsSlot(request.volumeLitre(), request.finishedProductId());
		}
		return slots.save(new StorageSlot(storageId, request)).getId();
	}

	/** Dolu raf silinmez; hareket gecmisi olan raf 422 (kullanildigi yerlerle). */
	@Transactional
	void deleteSlot(long storageId, long slotId) {
		find(storageId);
		StorageSlot slot = slots.findById(slotId).orElseThrow(() -> slotNotFound(slotId));
		if (slot.getCurrentQuantity() > 0) {
			throw new BusinessRuleException("Bu slotta ürün bulunduğu için silinemez.");
		}
		DeletionGuard.of("Bu slot")
			.usedBy("stok hareketi", movements.countBySlotId(slotId))
			.check();
		slots.delete(slot);
	}

	/**
	 * Kapasite kullanimin altina inemez, dolu rafin hacmi degismez, baska urun varken tek urune kilitlenemez;
	 * {@code null} gonderilerek kilit kaldirilir. Raf baska depoya ait olsa da kabul edilir.
	 */
	@Transactional
	void updateSlot(long storageId, long slotId, UpdateSlotRequest request) {
		find(storageId);
		StorageSlot slot = slots.findForUpdate(slotId).orElseThrow(() -> slotNotFound(slotId));
		Long requestedLock = request.finishedProductId() == null ? null : request.finishedProductId().value();
		if (requestedLock != null) {
			assertProductExists(requestedLock);
		}
		int current = slot.getCurrentQuantity();
		if (request.maxQuantity() != null && request.maxQuantity() < current) {
			throw new BusinessRuleException("Yeni max_quantity (" + request.maxQuantity() + ") mevcut kullanımın ("
				+ current + ") altına düşemez.");
		}
		boolean volumeChanges = request.volumeLitre() != null
			&& Quantities.differ(request.volumeLitre(), slot.getVolumeLitre());
		if (volumeChanges && current > 0) {
			throw new BusinessRuleException("Slot " + label(slot) + " içinde " + current
				+ " şişe varken hacmi değiştirilemez.");
		}
		BigDecimal effectiveVolume = request.volumeLitre() != null ? request.volumeLitre() : slot.getVolumeLitre();
		Long effectiveLock = request.finishedProductId() != null ? requestedLock : slot.getFinishedProductId();
		if (effectiveLock != null) {
			assertNoOtherProducts(slot, effectiveLock);
			assertProductFitsSlot(effectiveVolume, effectiveLock);
		}
		Patch.ifPresent(request.name(), slot::changeName);
		if (request.volumeLitre() != null) {
			slot.changeVolume(request.volumeLitre());
		}
		if (request.maxQuantity() != null) {
			slot.changeCapacity(request.maxQuantity());
		}
		Patch.ifPresent(request.finishedProductId(), slot::lockTo);
	}

	private void assertNoOtherProducts(StorageSlot slot, long lockId) {
		List<SlotStock> others = slotStocks.findBySlotIdOrderByIdAsc(slot.getId()).stream()
			.filter(stock -> !Objects.equals(stock.getFinishedProductId(), lockId) && stock.getQuantity() > 0)
			.toList();
		if (others.isEmpty()) {
			return;
		}
		List<String> names = new ArrayList<>();
		for (SlotStock stock : others) {
			products.findById(stock.getFinishedProductId()).map(FinishedProduct::getName)
				.filter(name -> !name.isEmpty()).ifPresent(names::add);
		}
		throw new BusinessRuleException("Slot " + label(slot) + " içinde başka ürünler var (" + String.join(", ", names)
			+ "); tek ürüne kilitlenemez.");
	}

	// ---- depoya yerlestirme -------------------------------------------------------

	/** Tek raf ya da birden cok raf, tek islemde. Acikta yeterli sise olup olmadigi once toplam uzerinden bakilir. */
	@Transactional
	void stockIn(long storageId, StockInRequest request, long actorId) {
		FinishedProductStorage storage = find(storageId);
		validateStockIn(request);
		FinishedProduct product = lockProduct(request.finishedProductId());
		List<StockInRequest.Placement> placements = request.placements() != null ? request.placements()
			: List.of(new StockInRequest.Placement(request.finishedProductStorageSlotId(), request.quantity()));
		assertStorageActive(storage);
		Set<Long> seen = new HashSet<>();
		for (StockInRequest.Placement placement : placements) {
			if (!seen.add(placement.finishedProductStorageSlotId())) {
				throw new BusinessRuleException("Aynı raf listede birden fazla kez verilemez.");
			}
		}
		long total = placements.stream().mapToLong(StockInRequest.Placement::quantity).sum();
		assertUnstoredAvailable(product, total);
		for (StockInRequest.Placement placement : placements) {
			StorageSlot slot = lockSlot(placement.finishedProductStorageSlotId());
			assertSlotBelongsToStorage(slot, storage);
			assertSlotAccepts(slot, product, placement.quantity());
			addToSlot(slot, product, placement.quantity());
			recordMovement(storage.getId(), slot.getId(), product.getId(), MovementType.STOCK_IN, placement.quantity(),
				actorId, request.note(), null, null, request.finishedProductRunId(), null);
		}
	}

	private void validateStockIn(StockInRequest request) {
		ValidationErrors errors = new ValidationErrors();
		if (!products.existsById(request.finishedProductId())) {
			errors.add("finished_product_id", LaravelMessages.EXISTS);
		}
		if (request.finishedProductRunId() != null && !runs.existsById(request.finishedProductRunId())) {
			errors.add("finished_product_run_id", LaravelMessages.EXISTS);
		}
		boolean noPlacements = request.placements() == null || request.placements().isEmpty();
		Long slotId = request.finishedProductStorageSlotId();
		if (noPlacements && slotId == null) {
			errors.add(SLOT_FIELD, LaravelMessages.REQUIRED_WITHOUT_PLACEMENTS);
		}
		else if (slotId != null && !slots.existsById(slotId)) {
			errors.add(SLOT_FIELD, LaravelMessages.EXISTS);
		}
		if (noPlacements && request.quantity() == null) {
			errors.add("quantity", LaravelMessages.REQUIRED_WITHOUT_PLACEMENTS);
		}
		if (noPlacements) {
			if (slotId == null) {
				errors.add("placements", LaravelMessages.REQUIRED_WITHOUT_SLOT);
			}
			else if (request.placements() != null) {
				errors.add("placements", "En az bir rafa miktar girilmelidir.");
			}
		}
		else {
			List<Long> slotIds = request.placements().stream()
				.map(StockInRequest.Placement::finishedProductStorageSlotId).toList();
			errors.exists("placements", SLOT_FIELD, slotIds, existingSlots(slotIds));
		}
		errors.throwIfAny();
	}

	/** Raftan rafa (ayni ya da baska depo); raflar id sirasiyla kilitlenir. @return hedef depo id */
	@Transactional
	long transfer(long sourceStorageId, TransferRequest request, long actorId) {
		FinishedProductStorage sourceStorage = find(sourceStorageId);
		ValidationErrors errors = new ValidationErrors();
		if (!products.existsById(request.finishedProductId())) {
			errors.add("finished_product_id", LaravelMessages.EXISTS);
		}
		if (!slots.existsById(request.sourceSlotId())) {
			errors.add("source_slot_id", LaravelMessages.EXISTS);
		}
		if (!slots.existsById(request.targetSlotId())) {
			errors.add("target_slot_id", LaravelMessages.EXISTS);
		}
		if (request.sourceSlotId().equals(request.targetSlotId())) {
			errors.add("target_slot_id", LaravelMessages.DIFFERENT_SOURCE_SLOT);
		}
		errors.throwIfAny();
		long sourceSlotId = request.sourceSlotId();
		long targetSlotId = request.targetSlotId();
		long quantity = request.quantity();
		Map<Long, StorageSlot> locked = new LinkedHashMap<>();
		for (long id : new long[] {Math.min(sourceSlotId, targetSlotId), Math.max(sourceSlotId, targetSlotId)}) {
			locked.put(id, lockSlot(id));
		}
		StorageSlot sourceSlot = locked.get(sourceSlotId);
		StorageSlot targetSlot = locked.get(targetSlotId);
		FinishedProduct product = products.findById(request.finishedProductId())
			.orElseThrow(() -> new ModelNotFoundException("FinishedProduct", request.finishedProductId()));
		assertSlotBelongsToStorage(sourceSlot, sourceStorage);
		long targetStorageId = targetSlot.getStorageId();
		FinishedProductStorage targetStorage = find(targetStorageId);
		assertStorageActive(targetStorage);
		assertSlotAccepts(targetSlot, product, quantity);
		removeFromSlot(sourceSlot, product, quantity);
		addToSlot(targetSlot, product, quantity);
		UUID reference = UUID.randomUUID();
		recordMovement(sourceStorage.getId(), sourceSlot.getId(), product.getId(), MovementType.TRANSFER_OUT, quantity,
			actorId, null, targetSlot.getId(), reference, null, null);
		recordMovement(targetStorage.getId(), targetSlot.getId(), product.getId(), MovementType.TRANSFER_IN, quantity,
			actorId, null, sourceSlot.getId(), reference, null, null);
		return targetStorage.getId();
	}

	/** Sise fabrikadan cikar: raftan ve {@code total_stock}'tan duser. */
	@Transactional
	void stockOut(long storageId, StockOutRequest request, long actorId) {
		FinishedProductStorage storage = find(storageId);
		ValidationErrors errors = new ValidationErrors();
		if (!products.existsById(request.finishedProductId())) {
			errors.add("finished_product_id", LaravelMessages.EXISTS);
		}
		if (!slots.existsById(request.finishedProductStorageSlotId())) {
			errors.add(SLOT_FIELD, LaravelMessages.EXISTS);
		}
		errors.throwIfAny();
		StorageSlot slot = lockSlot(request.finishedProductStorageSlotId());
		FinishedProduct product = lockProduct(request.finishedProductId());
		long quantity = request.quantity();
		assertSlotBelongsToStorage(slot, storage);
		removeFromSlot(slot, product, quantity);
		if (product.getTotalStock() < quantity) {
			throw new BusinessRuleException("'" + product.getName() + "' için total_stock (" + product.getTotalStock()
				+ ") depodaki stoktan az; kayıtlar tutarsız. Çıkış yapılamadı.");
		}
		product.releaseStock((int) quantity);
		recordMovement(storage.getId(), slot.getId(), product.getId(), MovementType.STOCK_OUT, quantity,
			actorId, null, null,
			null, null, null);
	}

	// ---- siparis ---------------------------------------------------------------

	/** Siparis icin raftan alinan: raf, urun, adet. */
	public record Pick(long slotId, long productId, long quantity) {
	}

	/** Sise raftan cikar ama fabrikadan cikmaz ({@code total_stock} sevkiyatta duser). Rafta o kadar yoksa hata. */
	@Transactional
	public void pickForOrder(long orderId, List<Pick> picks, Long actorId) {
		for (Pick pick : picks) {
			StorageSlot slot = lockSlot(pick.slotId());
			FinishedProduct product = lockProduct(pick.productId());
			removeFromSlot(slot, product, pick.quantity());
			recordMovement(slot.getStorageId(), slot.getId(), product.getId(), MovementType.ORDER_PICK,
				pick.quantity(), actorId,
				"Siparis #" + orderId + " icin raftan alindi.", null, null, null, orderId);
		}
		slots.flush();
	}

	/**
	 * Iptal edilen hazir siparisin siseleri alindiklari raflara; daha once geri konan dusulur, raf kapasitesi
	 * yeniden kontrol edilir.
	 */
	@Transactional
	public void returnOrderPicks(long orderId, Long actorId) {
		Map<String, Integer> returned = movements.findByOrderIdAndMovementType(orderId,
			MovementType.ORDER_RETURN).stream()
			.collect(Collectors.toMap(FinishedProductStorageService::key, StockMovement::getQuantity, Integer::sum));
		for (StockMovement pick : movements.findByOrderIdAndMovementType(orderId, MovementType.ORDER_PICK)) {
			long remaining = (long) pick.getQuantity() - returned.getOrDefault(key(pick), 0);
			if (remaining <= 0) {
				continue;
			}
			StorageSlot slot = lockSlot(pick.getSlotId());
			FinishedProduct product = lockProduct(pick.getFinishedProductId());
			assertSlotAccepts(slot, product, remaining);
			addToSlot(slot, product, remaining);
			recordMovement(slot.getStorageId(), slot.getId(), product.getId(), MovementType.ORDER_RETURN,
				remaining, actorId,
				"Siparis #" + orderId + " iptal edildi, rafa geri konuldu.", null, null, null, orderId);
		}
		slots.flush();
	}

	private static String key(StockMovement movement) {
		return movement.getSlotId() + ":" + movement.getFinishedProductId();
	}

	// ---- sayim yardimcilari ----------------------------------------------------------

	private void addToSlot(StorageSlot slot, FinishedProduct product, long quantity) {
		slot.add(quantity);
		slotStocks.findForUpdate(slot.getId(), product.getId())
			.orElseGet(() -> slotStocks.save(new SlotStock(slot.getId(), product.getId())))
			.add(quantity);
		storageStocks.findForUpdate(slot.getStorageId(), product.getId())
			.orElseGet(() -> storageStocks.save(new StorageStock(slot.getStorageId(), product.getId())))
			.add(quantity);
	}

	private void removeFromSlot(StorageSlot slot, FinishedProduct product, long quantity) {
		SlotStock slotStock = slotStocks.findForUpdate(slot.getId(), product.getId()).orElse(null);
		int inSlot = slotStock == null ? 0 : slotStock.getQuantity();
		if (inSlot < quantity) {
			throw new BusinessRuleException("Slot " + label(slot) + " içinde '" + product.getName() + "' yetersiz. "
				+ "Slotta: " + inSlot + ", istenen: " + quantity + ".");
		}
		StorageStock storageStock = storageStocks.findForUpdate(slot.getStorageId(), product.getId()).orElse(null);
		if (storageStock == null || storageStock.getQuantity() < quantity) {
			throw new BusinessRuleException("Depo stok kaydı slot içeriğiyle tutarsız ('" + product.getName()
				+ "'). İşlem yapılamadı.");
		}
		slot.remove(quantity);
		if (inSlot == quantity) {
			slotStocks.delete(slotStock);
		}
		else {
			slotStock.add(-quantity);
		}
		storageStock.add(-quantity);
	}

	private void recordMovement(long storageId, long slotId, long productId, MovementType type,
		long quantity, Long actorId,
		String note, Long counterpartySlotId, UUID reference, Long runId, Long orderId) {
		movements.save(new StockMovement(storageId, slotId, productId, type, quantity, actorId, note, counterpartySlotId,
			reference, runId, orderId, Timestamps.now()));
	}

	// ---- kontroller --------------------------------------------------------------------

	private StorageSlot lockSlot(long slotId) {
		return slots.findForUpdate(slotId).orElseThrow(() -> slotNotFound(slotId));
	}

	private FinishedProduct lockProduct(long productId) {
		return products.findForUpdate(productId)
			.orElseThrow(() -> new ModelNotFoundException("FinishedProduct", productId));
	}

	private FinishedProductStorage find(long id) {
		return storages.findById(id).orElseThrow(() -> new ModelNotFoundException("FinishedProductStorage", id));
	}

	private static ModelNotFoundException slotNotFound(long slotId) {
		return new ModelNotFoundException("FinishedProductStorageSlot", slotId);
	}

	private static String label(StorageSlot slot) {
		String name = slot.getName();
		return name != null && !name.isEmpty() && !"0".equals(name) ? "'" + name + "'" : "#" + slot.getId();
	}

	private static void assertSlotBelongsToStorage(StorageSlot slot, FinishedProductStorage storage) {
		if (!Objects.equals(slot.getStorageId(), storage.getId())) {
			throw new BusinessRuleException("Slot " + label(slot) + " '" + storage.getName() + "' deposuna ait değil.");
		}
	}

	private static void assertStorageActive(FinishedProductStorage storage) {
		if (!storage.isActive()) {
			throw new BusinessRuleException("'" + storage.getName() + "' deposu pasif; ürün girişi yapılamaz.");
		}
	}

	private static void assertSlotAccepts(StorageSlot slot, FinishedProduct product, long quantity) {
		if (Quantities.differ(slot.getVolumeLitre(), product.getVolumeLitre())) {
			throw new BusinessRuleException("Ürün hacmi (" + product.getVolumeLitre().toPlainString() + "L) slot "
				+ label(slot) + " hacmiyle (" + slot.getVolumeLitre().toPlainString() + "L) eşleşmiyor.");
		}
		Long locked = slot.getFinishedProductId();
		if (locked != null && !locked.equals(product.getId())) {
			throw new BusinessRuleException("Slot " + label(slot) + " farklı bir ürüne ayrılmış.");
		}
		int available = slot.available();
		if (quantity > available) {
			throw new BusinessRuleException("Slot " + label(slot) + " kapasitesi yetersiz. Boş kapasite: " + available
				+ ", istenen: " + quantity + ".");
		}
	}

	/** Depoya yalnizca hicbir depoya yerlesmemis (ve sevkiyat beklemeyen) sise girebilir. */
	private void assertUnstoredAvailable(FinishedProduct product, long quantity) {
		long stored = storageStocks.storedTotal(product.getId());
		long awaiting = directory.awaitingShipment(List.of(product.getId())).getOrDefault(product.getId(), 0L);
		long total = product.getTotalStock();
		long unstored = total - stored - awaiting;
		if (quantity > unstored) {
			throw new BusinessRuleException("'" + product.getName() + "' için depoya yerleştirilecek yeterli şişe yok. "
				+ "Fabrikada: " + total + ", depolarda: " + stored + ", sevkiyat bekleyen: " + awaiting + ", açıkta: "
				+ Math.max(0, unstored) + ", istenen: " + quantity + ". Depolar arası taşıma için transfer kullanın.");
		}
	}

	private void assertProductExists(long productId) {
		if (!products.existsById(productId)) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("finished_product_id", LaravelMessages.EXISTS);
			errors.throwIfAny();
		}
	}

	/** Kilitlenecek urunun hacmi rafinkiyle ayni olmali. */
	private void assertProductFitsSlot(BigDecimal volume, long productId) {
		products.findById(productId).ifPresent(product -> {
			if (Quantities.differ(product.getVolumeLitre(), volume)) {
				throw new BusinessRuleException("Ürün hacmi (" + product.getVolumeLitre().toPlainString()
					+ "L) slot hacmiyle (" + Quantities.plain(volume) + "L) eşleşmiyor.");
			}
		});
	}

	private List<Long> existingSlots(List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return List.of();
		}
		return slots.findAllById(present).stream().map(StorageSlot::getId).toList();
	}

}
