package com.olivamore.api.product.productionproduct;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.product.productattribute.ProductAttributeResponse;
import com.olivamore.api.product.productiontype.ProductionProductTypeResponse;
import com.olivamore.api.productionline.ProductionLineResponse;

import java.math.BigDecimal;
import java.util.List;

/** Urun kategorisinin API cevabi. {@code null} olan iliskiler o ekranda istenmemistir, cevapta yok. */
record ProductionProductResponse(
	Long id,
	String key,
	String name,
	String description,
	BigDecimal defaultDensityKgPerLitre,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Long productsCount,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<ProductAttributeResponse> attributeDefinitions,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<ProductionProductTypeResponse> productionProductTypes,
	List<ProductionLineResponse> productionLines) {

	static ProductionProductResponse basic(ProductionProduct product) {
		return full(product, null, null, null, List.of());
	}

	static ProductionProductResponse full(ProductionProduct product, Long count,
		List<ProductAttributeResponse> attributes, List<ProductionProductTypeResponse> types,
		List<ProductionLineResponse> lines) {
		return new ProductionProductResponse(product.getId(), product.getKey(), product.getName(),
			product.getDescription(), product.getDefaultDensityKgPerLitre(), count, attributes, types, lines);
	}

}
