package com.olivamore.api.product.productattribute;

import com.olivamore.api.product.ProductUsage;
import com.olivamore.api.product.ProductUsageCounter;
import com.olivamore.api.product.productionproduct.ProductionProductService;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.MessageKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProductAttributeService {

	private final ProductAttributeRepository mappings;
	private final AttributeDefinitionRepository definitions;
	private final ProductionProductService products;
	private final List<ProductUsageCounter> counters;

	ProductAttributeService(ProductAttributeRepository mappings, AttributeDefinitionRepository definitions,
		ProductionProductService products, List<ProductUsageCounter> counters) {
		this.mappings = mappings;
		this.definitions = definitions;
		this.products = products;
		this.counters = counters;
	}

	@Transactional(readOnly = true)
	List<AttributeDefinitionResponse> definitions() {
		return definitions.findAllByOrderByLabelAscIdAsc().stream().map(AttributeDefinitionResponse::of).toList();
	}

	@Transactional(readOnly = true)
	List<ProductAttributeResponse> forProduct(long productId) {
		products.ensureExists(productId);
		return mappings.findByProductionProductIdOrderBySortOrderAscIdAsc(productId).stream()
			.map(mapping -> ProductAttributeResponse.of(mapping, true)).toList();
	}

	/** Kategori detayinda: {@code definition} yok. */
	@Transactional(readOnly = true)
	public List<ProductAttributeResponse> summaries(long productId) {
		return mappings.findByProductionProductIdOrderByIdAsc(productId).stream()
			.map(mapping -> ProductAttributeResponse.of(mapping, false)).toList();
	}

	@Transactional(readOnly = true)
	ProductAttributeResponse get(long productId, long mappingId) {
		return ProductAttributeResponse.of(find(productId, mappingId), true);
	}

	/** Global tanim yoksa olusur; anahtar bu kategoride zaten eslenmisse 422. */
	@Transactional
	ProductAttributeResponse create(long productId, CreateProductAttributeRequest request) {
		products.ensureExists(productId);
		if (!request.dataType().assignable()) {
			throw ValidationFailedException.of("data_type", MessageKeys.IN);
		}
		if (mappings.existsByProductionProductIdAndDefinitionAttributeKey(productId, request.attributeKey())) {
			throw keyTaken();
		}
		AttributeDefinition definition = definitions.findByAttributeKey(request.attributeKey())
			.orElseGet(() -> definitions.save(new AttributeDefinition(request)));
		return ProductAttributeResponse.of(
			mappings.save(new ProductAttribute(productId, definition, request)), true);
	}

	@Transactional
	ProductAttributeResponse update(long productId, long mappingId, UpdateProductAttributeRequest request) {
		products.ensureExists(productId);
		if (request.attributeKey() != null && mappings.existsByProductionProductIdAndDefinitionAttributeKeyAndIdNot(
			productId, request.attributeKey(), mappingId)) {
			throw keyTaken();
		}
		ProductAttribute mapping = find(productId, mappingId);
		mapping.apply(request);
		mappings.flush();
		return ProductAttributeResponse.of(mapping, true);
	}

	@Transactional
	void delete(long productId, long mappingId) {
		ProductAttribute mapping = find(productId, mappingId);
		Map<ProductUsage, Long> usage =
			ProductUsageCounter.total(counters, counter -> counter.ofAttributeMapping(mappingId));
		DeletionGuard.of("'" + mapping.getDefinition().getLabel() + "' attribute eşlemesi")
			.usedBy("materyal değeri", usage.getOrDefault(ProductUsage.MATERIAL_ATTRIBUTE_VALUE, 0L))
			.usedBy("tank attribute kuralı", usage.getOrDefault(ProductUsage.TANK_ATTRIBUTE_RULE, 0L))
			.check();
		mappings.delete(mapping);
	}

	private ProductAttribute find(long productId, long mappingId) {
		products.ensureExists(productId);
		ProductAttribute mapping = mappings.findById(mappingId)
			.orElseThrow(() -> new ModelNotFoundException("ProductionProductAttributeDefinition", mappingId));
		if (mapping.getProductionProductId() != productId) {
			throw new BusinessRuleException("Seçilen attribute definition bu ürün kategorisine ait değil.");
		}
		return mapping;
	}

	private static ValidationFailedException keyTaken() {
		return ValidationFailedException.of("attribute_key",
			MessageKeys.UNIQUE);
	}

}
