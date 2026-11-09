package com.olivamore.api.sales.customer;

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

@Entity
@Table(name = "customer_preferred_recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CustomerPreferredRecipe extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long customerId;

	@Column(nullable = false)
	private Long finishedProductRecipeId;

	private String note;

	CustomerPreferredRecipe(long customerId, long recipeId, String note) {
		this.customerId = customerId;
		finishedProductRecipeId = recipeId;
		this.note = note;
	}

	void changeNote(String value) {
		note = value;
	}

}
