package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.finishedproduct.support.Quantities;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Tarifin attribute kurali: hedef deger ve kabul edilen sapmalar. */
@Entity
@Table(name = "finished_product_recipe_attribute_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeAttributeRule extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_recipe_id", nullable = false)
	private Long recipeId;

	@Column(nullable = false)
	private Long attributeDefinitionId;

	@Column(nullable = false)
	private BigDecimal value;

	private BigDecimal minDeviation;

	private BigDecimal maxDeviation;

	RecipeAttributeRule(long recipeId, long attributeDefinitionId, BigDecimal value, BigDecimal minDeviation,
		BigDecimal maxDeviation) {
		this.recipeId = recipeId;
		this.attributeDefinitionId = attributeDefinitionId;
		this.value = Quantities.normalize(value);
		this.minDeviation = Quantities.normalize(minDeviation);
		this.maxDeviation = Quantities.normalize(maxDeviation);
	}

}
