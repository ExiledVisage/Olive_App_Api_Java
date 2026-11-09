package com.olivamore.api.product.productiontype;

import com.olivamore.api.product.productionproduct.ProductionProduct;
import com.olivamore.api.product.productionproduct.ProductionProductService;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class ProductionProductTypeService {

	private final ProductionProductTypeRepository repository;
	private final ProductionProductService products;
	private final JdbcClient jdbc;

	ProductionProductTypeService(ProductionProductTypeRepository repository, ProductionProductService products,
		JdbcClient jdbc) {
		this.repository = repository;
		this.products = products;
		this.jdbc = jdbc;
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
		DeletionGuard.of("'" + type.getName() + "' ürün tipi")
			.usedBy("materyal", countBy("materials", id))
			.usedBy("parti girdisi", countBy("production_batch_input_production_product_types", id))
			.usedBy("parti çıktısı", countBy("production_batch_output_production_product_types", id))
			.usedBy("tank izni", countBy("storage_unit_allowed_production_product_types", id))
			.usedBy("tarif içeriği", countBy("finished_product_recipe_contents", id))
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

	private long countBy(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where production_product_type_id = ?")
			.param(id)
			.query(Long.class)
			.single();
	}

	private static LaravelValidationException codeTaken() {
		return LaravelValidationException.of("code", LaravelMessages.format(LaravelMessages.UNIQUE, "code"));
	}

}
