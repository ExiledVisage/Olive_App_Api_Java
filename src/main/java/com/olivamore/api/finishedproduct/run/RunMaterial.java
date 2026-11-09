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

/** Kosunun bir materyalden tanktan cekecegi pay. */
@Entity
@Table(name = "finished_product_run_materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RunMaterial extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "finished_product_run_id", nullable = false)
	private Long runId;

	@Column(nullable = false)
	private Long sourceMaterialId;

	@Column(nullable = false)
	private BigDecimal quantityUsed;

	RunMaterial(long runId, long sourceMaterialId, BigDecimal quantityUsed) {
		this.runId = runId;
		this.sourceMaterialId = sourceMaterialId;
		this.quantityUsed = Quantities.normalize(quantityUsed);
	}

}
