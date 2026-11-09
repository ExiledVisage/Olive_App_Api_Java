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

/** Tarifteki bir urun tipinin orani. */
@Entity
@Table(name = "finished_product_recipe_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeContent extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_recipe_id", nullable = false)
	private Long recipeId;

	@Column(nullable = false)
	private Long productionProductTypeId;

	@Column(nullable = false)
	private BigDecimal ratio;

	RecipeContent(long recipeId, long productionProductTypeId, BigDecimal ratio) {
		this.recipeId = recipeId;
		this.productionProductTypeId = productionProductTypeId;
		this.ratio = Quantities.normalize(ratio);
	}

}
