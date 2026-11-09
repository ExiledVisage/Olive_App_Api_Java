package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipe;
import com.olivamore.api.finishedproduct.support.Quantities;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "finished_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinishedProduct extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "finished_product_recipe_id")
	private FinishedProductRecipe recipe;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private BigDecimal volumeLitre;

	@Column(nullable = false)
	private int totalStock;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	FinishedProduct(String name, FinishedProductRecipe recipe, BigDecimal volumeLitre, Boolean active) {
		this.name = name;
		this.recipe = recipe;
		this.volumeLitre = Quantities.normalize(volumeLitre);
		if (active != null) {
			this.active = active;
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateFinishedProductRequest request, FinishedProductRecipe newRecipe) {
		if (request.name() != null) {
			name = request.name();
		}
		if (newRecipe != null) {
			recipe = newRecipe;
		}
		if (request.volumeLitre() != null) {
			volumeLitre = Quantities.normalize(request.volumeLitre());
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Cagiran satiri kilitlemis olmali. */
	public void releaseStock(int quantity) {
		totalStock -= quantity;
	}

}
