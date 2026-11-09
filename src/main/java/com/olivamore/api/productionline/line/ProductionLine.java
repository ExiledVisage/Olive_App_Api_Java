package com.olivamore.api.productionline.line;

import com.olivamore.api.shared.json.Patch;
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
@Table(name = "production_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductionLine extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String description;

	ProductionLine(CreateProductionLineRequest request) {
		name = request.name();
		description = request.description();
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateProductionLineRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		Patch.ifPresent(request.description(), value -> description = value);
	}

}
