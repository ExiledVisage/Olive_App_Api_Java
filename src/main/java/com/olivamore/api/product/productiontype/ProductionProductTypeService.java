package com.olivamore.api.product.productiontype;

import com.olivamore.api.product.ProductUsage;
import com.olivamore.api.product.ProductUsageCounter;
import com.olivamore.api.product.productionproduct.ProductionProduct;
import com.olivamore.api.product.productionproduct.ProductionProductService;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
class ProductionProductTypeService {

	private final ProductionProductTypeRepository repository;
	private final ProductionProductService products;
	private final List<ProductUsageCounter> counters;

	ProductionProductTypeService(ProductionProductTypeRepository repository, ProductionProductService products,
		List<ProductUsageCounter> counters) {
		this.repository = repository;
		this.products = products;
		this.counters = counters;
	}

	@Transactional(readOnly = true)
	List<ProductionProductTypeResponse> all() {
		return repository.findAllByOrderByCreatedAtDesc().stream().map(ProductionProductTypeResponses::of).toList();
	}

	@Transactional(readOnly = true)
	ProductionProductTypeResponse get(long id) {
		return ProductionProductTypeResponses.of(find(id));
	}

	/** Akis ve hat ekranlari icin: kategori ve tip kimlikleri. */
	@Transactional(readOnly = true)
	long productIdOf(long id) {
		return find(id).getProduct().getId();
	}

	@Transactional
	ProductionProductTypeResponse create(CreateProductionProductTypeRequest request) {
		if (repository.existsByCode(request.code())) {
			throw codeTaken();
		}
		ProductionProduct product = product(request.productionProductId());
		return ProductionProductTypeResponses.of(repository.save(new ProductionProductType(product, request)));
	}

	@Transactional
	ProductionProductTypeResponse update(long id, UpdateProductionProductTypeRequest request) {
		ProductionProductType type = find(id);
		if (request.code() != null && repository.existsByCodeAndIdNot(request.code(), id)) {
			throw codeTaken();
		}
		if (request.productionProductId() != null) {
			type.moveTo(product(request.productionProductId()));
		}
		type.apply(request);
		repository.flush();
		return ProductionProductTypeResponses.of(type);
	}

	@Transactional
	void delete(long id) {
		ProductionProductType type = find(id);
		Map<ProductUsage, Long> usage = ProductUsageCounter.total(counters, counter -> counter.ofProductType(id));
		DeletionGuard.of("'" + type.getName() + "' ürün tipi")
			.usedBy("materyal", usage.getOrDefault(ProductUsage.MATERIAL, 0L))
			.usedBy("parti girdisi", usage.getOrDefault(ProductUsage.BATCH_INPUT, 0L))
			.usedBy("parti çıktısı", usage.getOrDefault(ProductUsage.BATCH_OUTPUT, 0L))
			.usedBy("tank izni", usage.getOrDefault(ProductUsage.TANK_TYPE_PERMISSION, 0L))
			.usedBy("tarif içeriği", usage.getOrDefault(ProductUsage.RECIPE_CONTENT, 0L))
			.check();
		repository.delete(type);
	}

	private ProductionProductType find(long id) {
		return repository.findById(id).orElseThrow(() -> new ModelNotFoundException("ProductionProductType", id));
	}

	private ProductionProduct product(long id) {
		return products.findOptional(id).orElseThrow(() -> LaravelValidationException.of("production_product_id",
			LaravelMessages.format(LaravelMessages.EXISTS, "production_product_id")));
	}

	private static LaravelValidationException codeTaken() {
		return LaravelValidationException.of("code", LaravelMessages.format(LaravelMessages.UNIQUE, "code"));
	}

}
