package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.finishedproduct.product.FinishedProduct;
import com.olivamore.api.finishedproduct.product.FinishedProductRepository;
import com.olivamore.api.finishedproduct.storage.MovementResponse.CounterpartySlot;
import com.olivamore.api.finishedproduct.storage.SlotResponse.Content;
import com.olivamore.api.finishedproduct.storage.SlotResponse.IdName;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entity'den API cevabina: raflar, stok, hareket gecmisi. */
@Component
class FinishedProductStorageResponses {

	private record UserRow(Long id, String name) {
	}

	private final FinishedProductStorageRepository storages;
	private final StorageSlotRepository slots;
	private final SlotStockRepository slotStocks;
	private final StorageStockRepository storageStocks;
	private final StockMovementRepository movements;
	private final FinishedProductRepository products;
	private final UserDirectory userDirectory;

	FinishedProductStorageResponses(FinishedProductStorageRepository storages, StorageSlotRepository slots,
		SlotStockRepository slotStocks, StorageStockRepository storageStocks, StockMovementRepository movements,
		FinishedProductRepository products, UserDirectory userDirectory) {
		this.storages = storages;
		this.slots = slots;
		this.slotStocks = slotStocks;
		this.storageStocks = storageStocks;
		this.movements = movements;
		this.products = products;
		this.userDirectory = userDirectory;
	}

	@Transactional(readOnly = true)
	List<StorageResponse> list() {
		return responses(storages.findAll());
	}

	/** Raflar ve depo x urun stogu yuklu. */
	@Transactional(readOnly = true)
	StorageResponse detail(long id) {
		return responses(List.of(find(id))).getFirst();
	}

	/** Iliski yuklenmemis depo (ekleme cevabi). */
	@Transactional(readOnly = true)
	StorageResponse bare(long id) {
		return response(find(id), null, null);
	}

	/** Icerigi ve urun kilidiyle tek raf. */
	@Transactional(readOnly = true)
	SlotResponse slot(long id) {
		StorageSlot slot = slots.findById(id)
			.orElseThrow(() -> new ModelNotFoundException("FinishedProductStorageSlot", id));
		return slotResponses(List.of(slot)).getFirst();
	}

	/** Deponun hareket gecmisi, yeniden eskiye. */
	@Transactional(readOnly = true)
	List<MovementResponse> movements(long storageId) {
		find(storageId);
		List<StockMovement> rows = movements.findByStorageIdOrderByMovedAtDescIdDesc(storageId);
		List<Long> slotIds = new ArrayList<>(rows.stream().map(StockMovement::getSlotId).toList());
		slotIds.addAll(rows.stream().map(StockMovement::getCounterpartySlotId).toList());
		Map<Long, StorageSlot> slotsById = slots.findAllById(ValidationErrors.present(slotIds)).stream()
			.collect(Collectors.toMap(StorageSlot::getId, Function.identity()));
		Map<Long, String> storageNames =
			storages.findAllById(slotsById.values().stream().map(StorageSlot::getStorageId).distinct().toList())
			.stream().collect(Collectors.toMap(FinishedProductStorage::getId, FinishedProductStorage::getName));
		Map<Long, FinishedProduct> productsById =
			products(rows.stream().map(StockMovement::getFinishedProductId).toList());
		Map<Long, String> users = users(rows.stream().map(StockMovement::getCreatedByUserId).toList());
		return rows.stream().map(row -> {
			StorageSlot slot = slotsById.get(row.getSlotId());
			StorageSlot counterparty = row.getCounterpartySlotId() == null
				? null : slotsById.get(row.getCounterpartySlotId());
			FinishedProduct product = productsById.get(row.getFinishedProductId());
			return new MovementResponse(row.getId(), row.getMovementType(), row.getQuantity(),
				row.getMovementType().incoming() ? row.getQuantity() : -row.getQuantity(), row.getTransferReference(),
				row.getNote(), row.getMovedAt(), new IdName(product.getId(), product.getName()),
				new IdName(slot.getId(), slot.getName()),
				counterparty == null ? null : new CounterpartySlot(counterparty.getId(), counterparty.getName(),
					storageNames.get(counterparty.getStorageId())),
				row.getCreatedByUserId() == null ? null : idName(row.getCreatedByUserId(), users));
		}).toList();
	}

	private static IdName idName(Long id, Map<Long, String> names) {
		String name = names.get(id);
		return name == null ? null : new IdName(id, name);
	}

	private FinishedProductStorage find(long id) {
		return storages.findById(id).orElseThrow(() -> new ModelNotFoundException("FinishedProductStorage", id));
	}

	private List<StorageResponse> responses(List<FinishedProductStorage> rows) {
		List<Long> ids = rows.stream().map(FinishedProductStorage::getId).toList();
		List<StorageSlot> slotRows = ids.isEmpty() ? List.of() : slots.findByStorageIdInOrderByIdAsc(ids);
		List<StorageStock> stockRows = ids.isEmpty() ? List.of() : storageStocks.findByStorageIdInOrderByIdAsc(ids);
		List<SlotResponse> slotResponses = slotResponses(slotRows);
		Map<Long, List<SlotResponse>> slotsByStorage = new LinkedHashMap<>();
		for (int i = 0; i < slotRows.size(); i++) {
			slotsByStorage.computeIfAbsent(slotRows.get(i).getStorageId(),
				k -> new ArrayList<>()).add(slotResponses.get(i));
		}
		Map<Long, FinishedProduct> productsById =
			products(stockRows.stream().map(StorageStock::getFinishedProductId).toList());
		Map<Long, List<StorageResponse.Stock>> stockByStorage = stockRows.stream().collect(Collectors.groupingBy(
			StorageStock::getStorageId,
			Collectors.mapping(row -> {
				FinishedProduct product = productsById.get(row.getFinishedProductId());
				StorageResponse.StockProduct summary = product == null
					? null : new StorageResponse.StockProduct(product.getId(),
					product.getName(), product.getVolumeLitre().doubleValue());
				return new StorageResponse.Stock(row.getId(), summary, row.getQuantity(), row.getUpdatedAt());
			}, Collectors.toList())));
		return rows.stream()
			.map(storage -> response(storage, slotsByStorage.getOrDefault(storage.getId(), List.of()),
				stockByStorage.getOrDefault(storage.getId(), List.of())))
			.toList();
	}

	private static StorageResponse response(FinishedProductStorage storage, List<SlotResponse> slotList,
		List<StorageResponse.Stock> stock) {
		return new StorageResponse(storage.getId(), storage.getName(), storage.getDescription(), storage.isActive(),
			slotList, stock, storage.getCreatedAt(), storage.getUpdatedAt());
	}

	private List<SlotResponse> slotResponses(List<StorageSlot> rows) {
		List<Long> slotIds = rows.stream().map(StorageSlot::getId).toList();
		List<SlotStock> stockRows = slotIds.isEmpty() ? List.of() : slotStocks.findBySlotIdInOrderByIdAsc(slotIds);
		List<Long> productIds = new ArrayList<>(rows.stream().map(StorageSlot::getFinishedProductId).toList());
		productIds.addAll(stockRows.stream().map(SlotStock::getFinishedProductId).toList());
		Map<Long, FinishedProduct> productsById = products(productIds);
		Map<Long, List<SlotStock>> stocks = stockRows.stream().collect(Collectors.groupingBy(SlotStock::getSlotId));
		return rows.stream().map(slot -> {
			FinishedProduct locked = slot.getFinishedProductId() == null
				? null : productsById.get(slot.getFinishedProductId());
			List<Content> contents = stocks.getOrDefault(slot.getId(), List.of()).stream().map(stock -> {
				FinishedProduct product = productsById.get(stock.getFinishedProductId());
				return new Content(new IdName(stock.getFinishedProductId(), product == null ? null : product.getName()),
					stock.getQuantity());
			}).toList();
			return new SlotResponse(slot.getId(), slot.getName(), slot.getVolumeLitre().doubleValue(),
				slot.getMaxQuantity(),
				slot.getCurrentQuantity(), slot.available(), locked == null ? null : new IdName(locked.getId(),
					locked.getName()),
				contents, slot.getCreatedAt(), slot.getUpdatedAt());
		}).toList();
	}

	private Map<Long, FinishedProduct> products(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return products.findAllById(present).stream()
			.collect(Collectors.toMap(FinishedProduct::getId, Function.identity()));
	}

	private Map<Long, String> users(List<Long> ids) {
		return userDirectory.users(ids).values().stream()
			.collect(Collectors.toMap(UserDirectory.User::id, UserDirectory.User::name));
	}

}
