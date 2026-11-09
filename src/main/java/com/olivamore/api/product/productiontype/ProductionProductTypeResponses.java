package com.olivamore.api.product.productiontype;

import com.olivamore.api.product.productionproduct.ProductionProduct;
import com.olivamore.api.product.productiontype.ProductionProductTypeResponse.ProductSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Entity'den API cevabina. */
@Component
public class ProductionProductTypeResponses {

	private final ProductionProductTypeRepository repository;

	ProductionProductTypeResponses(ProductionProductTypeRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<ProductionProductTypeResponse> ofProduct(long productId) {
		return repository.findByProductIdOrderByIdAsc(productId).stream()
			.map(ProductionProductTypeResponses::of).toList();
	}

	static ProductionProductTypeResponse of(ProductionProductType type) {
		ProductionProduct product = type.getProduct();
		BigDecimal effective = type.effectiveDensity();
		return new ProductionProductTypeResponse(type.getId(), type.getName(), type.getCode(), type.getType(),
			type.getUnit(), type.getDensityKgPerLitre(), effective == null ? null : effective.doubleValue(),
			product.getDefaultDensityKgPerLitre(), product.getId(),
			new ProductSummary(product.getId(), product.getKey(), product.getName(), product.getDescription()),
			type.getCreatedAt(), type.getUpdatedAt());
	}

}
