package com.olivamore.api.finishedproduct.run;

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

/** Kosunun tahmini ve olculen attribute degeri. */
@Entity
@Table(name = "finished_product_run_attribute_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RunAttributeValue extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_run_id", nullable = false)
	private Long runId;

	@Column(nullable = false)
	private Long attributeDefinitionId;

	@Column(nullable = false)
	private BigDecimal estimatedValue;

	private BigDecimal actualValue;

	RunAttributeValue(long runId, long attributeDefinitionId, BigDecimal estimatedValue) {
		this.runId = runId;
		this.attributeDefinitionId = attributeDefinitionId;
		this.estimatedValue = Quantities.normalize(estimatedValue);
	}

	void recordActual(BigDecimal value) {
		actualValue = Quantities.normalize(value);
	}

}
