package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.PackagingAllocations.Need;
import com.olivamore.api.packaging.PackagingAllocations.Selection;
import com.olivamore.api.packaging.item.PackagingItem;
import com.olivamore.api.packaging.item.PackagingItemRepository;
import com.olivamore.api.packaging.lot.LotEnums.MovementType;
import com.olivamore.api.packaging.lot.LotEnums.Source;
import com.olivamore.api.packaging.lot.LotEnums.Status;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dolum kosusu tahsisi: FIFO, elle tahsis ve tamamlamada tuketim. Tahsis bir rezervasyon degil; tamamlamada
 * bir daha bakilir. Cagiran modul ayni islemde JDBC kullandigi icin degisiklikler hemen veritabanina yazilir.
 */
@Service
public class LotAllocationService {

	private static final Set<String> EDITABLE_RUN_STATUSES = Set.of("planned", "in_progress");

	private final PackagingLotRepository lots;
	private final RunPackagingRepository packagings;
	private final PackagingLotMovementRepository movements;
	private final PackagingItemRepository items;
	private final PackagingLotService lotService;

	LotAllocationService(PackagingLotRepository lots, RunPackagingRepository packagings,
		PackagingLotMovementRepository movements, PackagingItemRepository items, PackagingLotService lotService) {
		this.lots = lots;
		this.packagings = packagings;
		this.movements = movements;
		this.items = items;
		this.lotService = lotService;
	}

	/** Eski tahsis silinir, her kalem en eski girisli lottan baslayarak tahsis edilir. */
	@Transactional
	public void allocateForRun(long runId, List<Need> needs) {
		packagings.deleteForRun(runId);
		needs.forEach(need -> allocateItem(runId, need.itemId(), need.quantity()));
		packagings.flush();
	}

	private void allocateItem(long runId, long itemId, long needed) {
		if (needed <= 0) {
			return;
		}
		List<PackagingLot> active = lots.findByItemIdAndStatusAndQuantityRemainingGreaterThanOrderByReceivedAtAscIdAsc(
			itemId, Status.ACTIVE, 0);
		long available = active.stream().mapToLong(PackagingLot::getQuantityRemaining).sum();
		if (available < needed) {
			throw new BusinessRuleException(itemName(itemId) + " için " + needed + " adet gerekiyor, lotlarda " + available
				+ " adet var. Önce mal girişi yapın.");
		}
		long remaining = needed;
		for (PackagingLot lot : active) {
			if (remaining == 0) {
				break;
			}
			long take = Math.min(remaining, lot.getQuantityRemaining());
			packagings.save(new RunPackaging(runId, itemId, lot.getId(), (int) take));
			remaining -= take;
		}
	}

	/** Gonderilen liste tahsisin tamami; kalem basina toplam gereken adede esit olmali. */
	@Transactional
	public void syncRunAllocation(long runId, String runStatus, List<Need> needs, List<Selection> selections) {
		if (!EDITABLE_RUN_STATUSES.contains(runStatus)) {
			throw new BusinessRuleException("'" + runStatus + "' durumundaki koşunun ambalaj lotları değiştirilemez.");
		}
		Map<Long, Long> needed = new LinkedHashMap<>();
		needs.forEach(need -> needed.put(need.itemId(), need.quantity()));
		Map<Long, PackagingLot> found = lots.findByIdIn(selections.stream().map(Selection::lotId).toList()).stream()
			.collect(Collectors.toMap(PackagingLot::getId, Function.identity()));
		Map<Long, Long> allocated = new LinkedHashMap<>();
		List<RunPackaging> prepared = new ArrayList<>();
		for (Selection selection : selections) {
			PackagingLot lot = found.get(selection.lotId());
			if (lot == null) {
				throw new BusinessRuleException("#" + selection.lotId() + " numaralı lot bulunamadı.");
			}
			long itemId = lot.getItem().getId();
			if (!needed.containsKey(itemId)) {
				throw new BusinessRuleException("'" + lot.getLotCode() + "' lotu (" + itemName(itemId) + ") bu ürünün "
					+ "ambalaj listesinde yok.");
			}
			if (selection.quantity() > lot.getQuantityRemaining()) {
				throw new BusinessRuleException("'" + lot.getLotCode() + "' lotunda " + lot.getQuantityRemaining()
					+ " adet var, " + selection.quantity() + " adet seçildi.");
			}
			allocated.merge(itemId, selection.quantity(), Long::sum);
			prepared.add(new RunPackaging(runId, itemId, lot.getId(), (int) selection.quantity()));
		}
		needed.forEach((itemId, required) -> {
			long given = allocated.getOrDefault(itemId, 0L);
			if (given != required) {
				throw new BusinessRuleException(itemNameOrId(itemId) + " için " + required + " adet gerekiyor, " + given
					+ " adet seçildi.");
			}
		});
		packagings.deleteForRun(runId);
		packagings.saveAll(prepared);
		packagings.flush();
	}

	/** Tahsis yoksa FIFO ile kurulur, hala yeterli mi bakilir, lotlardan dusulur, cikis hareketleri yazilir. */
	@Transactional
	public void consumeForRun(long runId, List<Need> needs, long userId) {
		if (!packagings.existsByFinishedProductRunId(runId)) {
			allocateForRun(runId, needs);
		}
		assertAllocationStillAvailable(runId, needs);
		Set<Long> touched = new LinkedHashSet<>();
		for (RunPackaging allocation : packagings.findByFinishedProductRunIdOrderByIdAsc(runId)) {
			PackagingLot lot = lots.findForUpdate(allocation.getPackagingLotId()).orElseThrow();
			lot.changeRemaining(lot.getQuantityRemaining() - allocation.getQuantity());
			movements.save(new PackagingLotMovement(lot, MovementType.OUT, Source.FINISHED_PRODUCT_RUN,
				allocation.getQuantity(), "Dolum Run #" + runId, Timestamps.now(), userId, runId));
			touched.add(lot.getItem().getId());
		}
		touched.forEach(lotService::recalculateItemStock);
		lots.flush();
	}

	/** Tahsisli lotlarda yeterli adet ve kalem basina tam tahsis. */
	@Transactional(readOnly = true)
	public void assertAllocationStillAvailable(long runId, List<Need> needs) {
		List<RunPackaging> allocations = packagings.findByFinishedProductRunIdOrderByIdAsc(runId);
		Map<Long, PackagingLot> lotsById = lots.findByIdIn(
				allocations.stream().map(RunPackaging::getPackagingLotId).toList()).stream()
			.collect(Collectors.toMap(PackagingLot::getId, Function.identity()));
		List<String> problems = new ArrayList<>();
		for (RunPackaging allocation : allocations) {
			PackagingLot lot = lotsById.get(allocation.getPackagingLotId());
			if (lot.getQuantityRemaining() < allocation.getQuantity()) {
				problems.add("'" + lot.getLotCode() + "' lotunda " + lot.getQuantityRemaining() + " adet kaldı, "
					+ allocation.getQuantity() + " adet gerekiyor");
			}
		}
		for (Need need : needs) {
			long given = allocations.stream()
				.filter(allocation -> allocation.getPackagingItemId() == need.itemId())
				.mapToLong(RunPackaging::getQuantity)
				.sum();
			if (given != need.quantity()) {
				problems.add(itemNameOrId(need.itemId()) + " için " + need.quantity() + " adet gerekiyor, " + given
					+ " adet tahsisli");
			}
		}
		if (!problems.isEmpty()) {
			throw new BusinessRuleException("Ambalaj tahsisi tamamlanamıyor: " + String.join("; ", problems)
				+ ". Lot seçimini güncelleyip yeniden deneyin.");
		}
	}

	/** Kalemin aktif lotlarinin kalani (onbellege degil lotlara bakar). */
	@Transactional(readOnly = true)
	public long availableQuantity(long itemId) {
		return lots.sumRemaining(itemId, Status.ACTIVE);
	}

	private String itemName(long itemId) {
		return items.findById(itemId).map(PackagingItem::getName).orElse(null);
	}

	private String itemNameOrId(long itemId) {
		String name = itemName(itemId);
		return name != null ? name : "#" + itemId;
	}

}
