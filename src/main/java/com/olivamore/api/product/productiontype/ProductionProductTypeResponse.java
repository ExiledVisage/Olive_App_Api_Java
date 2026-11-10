package com.olivamore.api.product.productiontype;


import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Urun tipinin API cevabi. */
public record ProductionProductTypeResponse(
	Long id,
	String name,
	String code,
	String type,
	ProductUnit unit,
	BigDecimal densityKgPerLitre,
	Double effectiveDensityKgPerLitre,
	BigDecimal defaultDensityKgPerLitre,
	Long productionProductId,
	ProductSummary productionProduct,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public record ProductSummary(Long id, String key, String name, String description) {
	}

}
