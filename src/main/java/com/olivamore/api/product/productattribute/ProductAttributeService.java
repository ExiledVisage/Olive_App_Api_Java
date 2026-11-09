package com.olivamore.api.product.productattribute;

import com.olivamore.api.product.productionproduct.ProductionProductService;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductAttributeService {

	private final ProductAttributeRepository mappings;
	private final AttributeDefinitionRepository definitions;
	private final ProductionProductService products;
	private final JdbcClient jdbc;

	ProductAttributeService(ProductAttributeRepository mappings, AttributeDefinitionRepository definitions,
		ProductionProductService products, JdbcClient jdbc) {
		this.mappings = mappings;
		this.definitions = definitions;
		this.products = products;
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	List<AttributeDefinitionResponse> definitions() {
		return definitions.findAllByOrderByLabelAsc().stream().map(AttributeDefinitionResponse::of).toList();
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
			throw LaravelValidationException.of("data_type", LaravelMessages.format(LaravelMessages.IN, "data_type"));
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
		DeletionGuard.of("'" + mapping.getDefinition().getLabel() + "' attribute eşlemesi")
			.usedBy("materyal değeri", countUsage("material_attribute_values", mappingId))
			.usedBy("tank attribute kuralı", countUsage("storage_unit_attribute_rules", mappingId))
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

	private long countUsage(String table, long mappingId) {
		return jdbc.sql("select count(*) from " + table + " where production_product_attribute_definition_id = ?")
			.param(mappingId)
			.query(Long.class)
			.single();
	}

	private static LaravelValidationException keyTaken() {
		return LaravelValidationException.of("attribute_key",
			LaravelMessages.format(LaravelMessages.UNIQUE, "attribute_key"));
	}

}
