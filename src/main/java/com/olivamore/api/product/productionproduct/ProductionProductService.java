package com.olivamore.api.product.productionproduct;

import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.product.ProductUsage;
import com.olivamore.api.product.ProductUsageCounter;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductionProductService {

	private final ProductionProductRepository repository;
	private final ProductDirectory directory;
	private final ProductionLineResources lines;
	private final List<ProductUsageCounter> counters;

	ProductionProductService(ProductionProductRepository repository, ProductDirectory directory,
		ProductionLineResources lines, List<ProductUsageCounter> counters) {
		this.repository = repository;
		this.directory = directory;
		this.lines = lines;
		this.counters = counters;
	}

	@Transactional(readOnly = true)
	List<ProductionProduct> all() {
		return repository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	Map<Long, Long> typeCounts() {
		return repository.countTypes().stream().collect(Collectors.toMap(
			ProductionProductRepository.TypeCount::getProductId, ProductionProductRepository.TypeCount::getTotal));
	}

	@Transactional(readOnly = true)
	long typeCount(long id) {
		return repository.countTypes(id);
	}

	@Transactional(readOnly = true)
	ProductionProduct get(long id) {
		return find(id);
	}

	@Transactional
	ProductionProduct create(CreateProductionProductRequest request) {
		ValidationErrors errors = new ValidationErrors();
		if (repository.existsByKey(request.key())) {
			errors.add("key", LaravelMessages.UNIQUE);
		}
		if (repository.existsByName(request.name())) {
			errors.add("name", LaravelMessages.UNIQUE);
		}
		errors.throwIfAny();
		return repository.save(new ProductionProduct(request));
	}

	@Transactional
	ProductionProduct update(long id, UpdateProductionProductRequest request) {
		ProductionProduct product = find(id);
		ValidationErrors errors = new ValidationErrors();
		if (request.key() != null && repository.existsByKeyAndIdNot(request.key(), id)) {
			errors.add("key", LaravelMessages.UNIQUE);
		}
		if (request.name() != null && repository.existsByNameAndIdNot(request.name(), id)) {
			errors.add("name", LaravelMessages.UNIQUE);
		}
		errors.throwIfAny();
		product.apply(request);
		return product;
	}

	@Transactional
	void delete(long id) {
		ProductionProduct product = find(id);
		Set<Long> mappingIds = directory.attributeMappingIdsOfProduct(id);
		Map<ProductUsage, Long> usage =
			ProductUsageCounter.total(counters, counter -> counter.ofProduct(id, mappingIds));
		ProductionLineResources.ProductUse lineUse = lines.productUse(id);
		DeletionGuard.of("'" + product.getName() + "' ürün kategorisi")
			.usedBy("ürün tipi", typeCount(id))
			.usedBy("tank bakiyesi", usage.getOrDefault(ProductUsage.TANK_BALANCE, 0L))
			.usedBy("tank izni", usage.getOrDefault(ProductUsage.TANK_PRODUCT_PERMISSION, 0L))
			.usedBy("hat girdisi", lineUse.lineInputs())
			.usedBy("hat çıktısı", lineUse.lineOutputs())
			.usedBy("aşama extension girdisi", lineUse.extensionInputs())
			.usedBy("aşama extension çıktısı", lineUse.extensionOutputs())
			.usedBy("materyal attribute değeri", usage.getOrDefault(ProductUsage.MATERIAL_ATTRIBUTE_VALUE, 0L))
			.usedBy("tank attribute kuralı", usage.getOrDefault(ProductUsage.TANK_ATTRIBUTE_RULE, 0L))
			.check();
		repository.delete(product);
	}

	/** Kategori yoksa 404. */
	@Transactional(readOnly = true)
	public void ensureExists(long id) {
		if (!repository.existsById(id)) {
			throw new ModelNotFoundException("ProductionProduct", id);
		}
	}

	@Transactional(readOnly = true)
	public Optional<ProductionProduct> findOptional(long id) {
		return repository.findById(id);
	}

	private ProductionProduct find(long id) {
		return repository.findById(id).orElseThrow(() -> new ModelNotFoundException("ProductionProduct", id));
	}

}
