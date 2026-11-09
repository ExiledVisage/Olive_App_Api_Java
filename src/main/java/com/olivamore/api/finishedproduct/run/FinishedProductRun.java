package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.finishedproduct.product.FinishedProduct;
import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipe;
import com.olivamore.api.shared.persistence.AuditedEntity;
import com.olivamore.api.shared.time.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.LocalDateTime;

/** Dolum kosusu. */
@Entity
@Table(name = "finished_product_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinishedProductRun extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "finished_product_id")
	private FinishedProduct product;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "finished_product_recipe_id")
	private FinishedProductRecipe recipe;

	private Long sourceStorageUnitId;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false)
	@Convert(converter = RunStatus.StatusConverter.class)
	private RunStatus status = RunStatus.PLANNED;

	private String note;

	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	FinishedProductRun(FinishedProduct product, FinishedProductRecipe recipe, long sourceStorageUnitId, int quantity,
		String note) {
		this.product = product;
		this.recipe = recipe;
		this.sourceStorageUnitId = sourceStorageUnitId;
		this.quantity = quantity;
		this.note = note;
	}

	void start() {
		status = RunStatus.IN_PROGRESS;
		startedAt = Timestamps.now();
	}

	void complete() {
		status = RunStatus.COMPLETED;
		completedAt = Timestamps.now();
	}

	void cancel() {
		status = RunStatus.CANCELLED;
	}

}
