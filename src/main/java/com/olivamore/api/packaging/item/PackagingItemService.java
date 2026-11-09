package com.olivamore.api.packaging.item;

import com.olivamore.api.packaging.lot.PackagingLotService;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class PackagingItemService {

	private final PackagingItemRepository repository;
	private final PackagingLotService lots;

	PackagingItemService(PackagingItemRepository repository, PackagingLotService lots) {
		this.repository = repository;
		this.lots = lots;
	}

	/** En yeni once. */
	@Transactional(readOnly = true)
	List<PackagingItemResponse> all(Boolean isActive) {
		List<PackagingItem> items = isActive == null
			? repository.findAllByOrderByCreatedAtDesc()
			: repository.findByActiveOrderByCreatedAtDesc(isActive);
		Map<Long, PackagingItemRepository.LotCounts> counts = counts();
		return items.stream().map(item -> response(item, counts)).toList();
	}

	@Transactional(readOnly = true)
	PackagingItemResponse get(long id) {
		return response(find(id), counts());
	}

	/** {@code currentStock} verilirse devir lotu olarak yazilir. */
	@Transactional
	long create(CreatePackagingItemRequest request, long userId) {
		PackagingItem item = repository.save(new PackagingItem(request));
		if (request.currentStock() != null && request.currentStock() > 0) {
			String lotCode = String.format("DEVIR-%d-%04d", Timestamps.now().getYear(), item.getId());
			lots.receive(item.getId(), lotCode, request.currentStock(), null, null,
				"Kalem tanımlanırken girilen devir stoğu.", userId);
		}
		return item.getId();
	}

	@Transactional
	void update(long id, UpdatePackagingItemRequest request) {
		find(id).apply(request);
	}

	@Transactional
	void delete(long id) {
		PackagingItem item = find(id);
		if (repository.countComponents(id) > 0) {
			throw new BusinessRuleException("Bu ambalaj kalemi bir ürün bileşeninde kullanıldığı için silinemez.");
		}
		if (lots.hasLots(id)) {
			throw new BusinessRuleException("Bu ambalaj kaleminin lot kayıtları var; silinemez. Pasife alabilirsiniz.");
		}
		repository.delete(item);
	}

	private PackagingItem find(long id) {
		return repository.findById(id).orElseThrow(() -> new ModelNotFoundException("PackagingItem", id));
	}

	private Map<Long, PackagingItemRepository.LotCounts> counts() {
		return repository.countLots().stream()
			.collect(Collectors.toMap(PackagingItemRepository.LotCounts::getItemId, Function.identity()));
	}

	private static PackagingItemResponse response(PackagingItem item,
		Map<Long, PackagingItemRepository.LotCounts> counts) {
		PackagingItemRepository.LotCounts count = counts.get(item.getId());
		return PackagingItemResponse.of(item, count == null ? 0 : count.getTotal(),
			count == null ? 0 : count.getActive());
	}

}
