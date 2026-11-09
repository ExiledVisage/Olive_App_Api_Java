package com.olivamore.api.finishedproduct;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Tarifin API cevabi. {@code attribute_rules} musteri profilinde yok. */
public record RecipeResponse(
	Long id,
	String name,
	int version,
	boolean isActive,
	String description,
	List<Content> contents,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<AttributeRule> attributeRules,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public record Content(
		Long id,
		Long productionProductTypeId,
		String productionProductTypeName,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal ratio) {
	}

	public record AttributeRule(
		Long id,
		Long attributeDefinitionId,
		String attributeLabel,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal value,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal minDeviation,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal maxDeviation) {
	}

	/** Urun ve kosu cevaplarindaki tarif ozeti; {@code contents} yalnizca detayda. */
	public record Summary(
		Long id,
		String name,
		int version,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		List<Content> contents) {
	}

}
