package com.olivamore.api.finishedproduct.recipe;

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

/** Dolum tarifi. Guncelleme yeni versiyon olusturur. */
@Entity
@Table(name = "finished_product_recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinishedProductRecipe extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int version;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	private String description;

	FinishedProductRecipe(String name, String description, int version) {
		this.name = name;
		this.description = description;
		this.version = version;
	}

	void deactivate() {
		active = false;
	}

}
