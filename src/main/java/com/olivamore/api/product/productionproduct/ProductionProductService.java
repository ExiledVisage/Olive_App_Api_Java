package com.olivamore.api.product.productionproduct;

import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductionProductService {

	private static final String ATTRIBUTE_IDS =
		"(select id from production_product_attribute_definitions where production_product_id = ?)";

	private final ProductionProductRepository repository;
	private final JdbcClient jdbc;

	ProductionProductService(ProductionProductRepository repository, JdbcClient jdbc) {
		this.repository = repository;
		this.jdbc = jdbc;
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
		DeletionGuard.of("'" + product.getName() + "' ürün kategorisi")
			.usedBy("ürün tipi", countBy("production_product_types", id))
			.usedBy("tank bakiyesi", countBy("storage_unit_balances", id))
			.usedBy("tank izni", countBy("storage_unit_allowed_production_products", id))
			.usedBy("hat girdisi", countBy("production_line_input_production_products", id))
			.usedBy("hat çıktısı", countBy("production_line_output_production_products", id))
			.usedBy("aşama extension girdisi", countBy("production_stage_extension_input_production_products", id))
			.usedBy("aşama extension çıktısı", countBy("production_stage_extension_output_production_products", id))
			.usedBy("materyal attribute değeri", countByAttribute("material_attribute_values", id))
			.usedBy("tank attribute kuralı", countByAttribute("storage_unit_attribute_rules", id))
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

	private long countBy(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where production_product_id = ?")
			.param(id)
			.query(Long.class)
			.single();
	}

	private long countByAttribute(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where production_product_attribute_definition_id in "
				+ ATTRIBUTE_IDS)
			.param(id)
			.query(Long.class)
			.single();
	}

}
