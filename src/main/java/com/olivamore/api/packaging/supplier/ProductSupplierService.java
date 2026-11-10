package com.olivamore.api.packaging.supplier;

import com.olivamore.api.packaging.lot.PackagingLotResponses;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.MessageKeys;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
class ProductSupplierService {

	private final ProductSupplierRepository repository;
	private final PackagingLotResponses lots;

	ProductSupplierService(ProductSupplierRepository repository, PackagingLotResponses lots) {
		this.repository = repository;
		this.lots = lots;
	}

	@Transactional(readOnly = true)
	List<ProductSupplierResponse> all(Boolean isActive, String search) {
		Map<Long, Long> counts = repository.countLots().stream().collect(Collectors.toMap(
			ProductSupplierRepository.LotCount::getSupplierId, ProductSupplierRepository.LotCount::getTotal));
		return repository.findAll(ProductSupplierRepository.matching(isActive, search), Sort.by("name")).stream()
			.map(s -> ProductSupplierResponse.of(s, counts.getOrDefault(s.getId(), 0L), null))
			.toList();
	}

	/** Lot sayisi ve lotlar. */
	@Transactional(readOnly = true)
	ProductSupplierResponse detail(long id) {
		return ProductSupplierResponse.of(find(id), repository.countLots(id), lots.ofSupplier(id));
	}

	@Transactional
	long create(CreateProductSupplierRequest request) {
		if (repository.existsByCode(request.code())) {
			throw codeTaken();
		}
		return repository.save(new ProductSupplier(request)).getId();
	}

	@Transactional
	void update(long id, UpdateProductSupplierRequest request) {
		ProductSupplier supplier = find(id);
		if (request.code() != null && repository.existsByCodeAndIdNot(request.code(), id)) {
			throw codeTaken();
		}
		supplier.apply(request);
	}

	/** Lotu olan tedarikci silinmez; pasife alinir. */
	@Transactional
	void delete(long id) {
		ProductSupplier supplier = find(id);
		DeletionGuard.of("'" + supplier.getName() + "' tedarikçisi")
			.usedBy("ambalaj lotu", repository.countLots(id))
			.check("Pasife alabilirsiniz.");
		repository.delete(supplier);
	}

	private ProductSupplier find(long id) {
		return repository.findById(id).orElseThrow(() -> new ModelNotFoundException("ProductSupplier", id));
	}

	private static ValidationFailedException codeTaken() {
		return ValidationFailedException.of("code", MessageKeys.UNIQUE);
	}

}
